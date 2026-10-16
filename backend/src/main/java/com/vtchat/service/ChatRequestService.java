package com.vtchat.service;

import com.vtchat.dto.chatrequest.ChatRequestEvent;
import com.vtchat.dto.chatrequest.ChatRequestResponse;
import com.vtchat.dto.chatrequest.ChatRequestResult;
import com.vtchat.dto.chatrequest.ChatRequestStatusResponse;
import com.vtchat.dto.conversation.ConversationResponse;
import com.vtchat.entity.ChatRequest;
import com.vtchat.entity.ChatRequestStatus;
import com.vtchat.entity.Conversation;
import com.vtchat.exception.ApiException;
import com.vtchat.redis.AfterCommit;
import com.vtchat.repository.ChatRequestRepository;
import com.vtchat.repository.ConversationRepository;
import com.vtchat.repository.UserRepository;
import com.vtchat.websocket.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, noRollbackFor = ApiException.class)
public class ChatRequestService {

    static final int DEFAULT_COOLDOWN_HOURS = 24;
    static final int MAX_OUTGOING_PENDING = 50;
    static final int LIST_LIMIT = 100;

    private final ChatRequestRepository requests;
    private final ChatRequestWriter writer;
    private final ConversationRepository conversations;
    private final UserRepository users;
    private final ConversationStateService conversationStates;
    private final NotificationEventPublisher events;
    private final NotificationDispatcher notificationDispatcher;
    private final ChatPermissionService permissions;

    @Value("${app.chat-requests.cooldown-hours:" + DEFAULT_COOLDOWN_HOURS + "}")
    private long cooldownHours = DEFAULT_COOLDOWN_HOURS;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ChatRequestResult send(Long senderId, Long receiverId) {
        if (receiverId == null) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Choose a user");
        }
        if (senderId.equals(receiverId)) {
            throw ApiException.badRequest("SELF_REQUEST", "You cannot send a chat request to yourself");
        }
        users.findById(receiverId).filter(u -> u.isEmailVerified())
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));

        Long low = Math.min(senderId, receiverId);
        Long high = Math.max(senderId, receiverId);

        Optional<Conversation> existing = conversations.findByUserOneIdAndUserTwoId(low, high);
        if (existing.isPresent() && permissions.isMessagingAllowed(senderId, receiverId)) {
            return connected(senderId, existing.get());
        }
        Optional<ChatRequest> pending = requests.findPending(ChatRequest.pairKey(senderId, receiverId));
        if (pending.isPresent()) {
            return result(senderId, pending.get());
        }
        Optional<ChatRequest> declined = requests.findFirstBySenderIdAndReceiverIdAndStatusOrderByIdDesc(senderId, receiverId, ChatRequestStatus.DECLINED);
        if (declined.isPresent() && retryAt(declined.get()).isAfter(Instant.now())) {
            throw ApiException.conflict("REQUEST_DECLINED", "This person declined your request. You can try again later.");
        }
        if (requests.countBySenderIdAndStatus(senderId, ChatRequestStatus.PENDING) >= MAX_OUTGOING_PENDING) {
            throw ApiException.tooManyRequests("TOO_MANY_PENDING_REQUESTS", "You have too many pending chat requests", 3600);
        }

        ChatRequest created;
        try {
            created = writer.insertPending(senderId, receiverId);
        } catch (DataIntegrityViolationException raced) {
            created = requests.findPending(ChatRequest.pairKey(senderId, receiverId))
                    .orElseThrow(() -> ApiException.conflict("REQUEST_CONFLICT", "Please try again"));
            return result(senderId, created);
        }
        events.chatRequest(receiverId, new ChatRequestEvent(ChatRequestEvent.RECEIVED, created.getId(), senderId, null));
        notificationDispatcher.onChatRequestReceived(receiverId, senderId, created.getId());
        return result(senderId, created);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public ChatRequestResult accept(Long userId, Long requestId) {
        ChatRequest request = lockForReceiver(userId, requestId);
        if (request.getStatus() == ChatRequestStatus.ACCEPTED) {
            return acceptedResult(userId, request);
        }
        if (request.getStatus() == ChatRequestStatus.DECLINED) {
            throw ApiException.conflict("REQUEST_ALREADY_DECLINED", "This request was already declined");
        }
        if (request.getStatus() == ChatRequestStatus.ENDED) {
            throw ApiException.conflict("REQUEST_ENDED", "This connection was ended. Send a new chat request.");
        }
        Long senderId = request.getSender().getId();
        Long low = request.getPairLowId();
        Long high = request.getPairHighId();
        Conversation conversation = conversations.findByUserOneIdAndUserTwoId(low, high)
                .orElseGet(() -> conversations.saveAndFlush(Conversation.builder()
                        .userOne(users.getReferenceById(low))
                        .userTwo(users.getReferenceById(high))
                        .build()));
        request.setStatus(ChatRequestStatus.ACCEPTED);
        request.setRespondedAt(Instant.now());
        request.setPendingPairKey(null);
        requests.save(request);
        conversationStates.undelete(userId, conversation.getId());
        conversationStates.undelete(senderId, conversation.getId());
        Long conversationId = conversation.getId();
        AfterCommit.run(() -> {
            events.chatRequest(senderId, new ChatRequestEvent(ChatRequestEvent.ACCEPTED, requestId, userId, conversationId));
            events.chatRequest(userId, new ChatRequestEvent(ChatRequestEvent.ACCEPTED, requestId, senderId, conversationId));
            notificationDispatcher.onChatRequestAnswered(userId, senderId, requestId, true, conversationId);
        });
        return acceptedResult(userId, request);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public ChatRequestResult decline(Long userId, Long requestId) {
        ChatRequest request = lockForReceiver(userId, requestId);
        if (request.getStatus() == ChatRequestStatus.ACCEPTED) {
            throw ApiException.conflict("REQUEST_ALREADY_ACCEPTED", "This request was already accepted");
        }
        if (request.getStatus() == ChatRequestStatus.ENDED) {
            throw ApiException.conflict("REQUEST_ENDED", "This connection was ended. Send a new chat request.");
        }
        if (request.getStatus() == ChatRequestStatus.PENDING) {
            Long senderId = request.getSender().getId();
            request.setStatus(ChatRequestStatus.DECLINED);
            request.setRespondedAt(Instant.now());
            request.setPendingPairKey(null);
            requests.save(request);
            AfterCommit.run(() -> {
                events.chatRequest(senderId, new ChatRequestEvent(ChatRequestEvent.DECLINED, requestId, userId, null));
                events.chatRequest(userId, new ChatRequestEvent(ChatRequestEvent.DECLINED, requestId, senderId, null));
                notificationDispatcher.onChatRequestAnswered(userId, senderId, requestId, false, null);
            });
        }
        return new ChatRequestResult(ChatRequestResponse.of(request), relationshipFor(userId, request.getSender().getId()), null);
    }

    private ChatRequest lockForReceiver(Long userId, Long requestId) {
        ChatRequest request = requests.lockById(requestId)
                .orElseThrow(() -> ApiException.notFound("REQUEST_NOT_FOUND", "Chat request not found"));
        if (!request.getReceiver().getId().equals(userId)) {
            throw ApiException.forbidden("FORBIDDEN", "Only the person who received this request can answer it");
        }
        return request;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public ChatRequestStatusResponse unfriend(Long userId, Long targetId) {
        if (targetId == null) {
            throw ApiException.badRequest("VALIDATION_ERROR", "Choose a user");
        }
        if (userId.equals(targetId)) {
            throw ApiException.badRequest("SELF_UNFRIEND", "You cannot remove yourself");
        }
        var target = users.findById(targetId).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
        Long low = Math.min(userId, targetId);
        Long high = Math.max(userId, targetId);
        Optional<Conversation> conversation = conversations.findByUserOneIdAndUserTwoId(low, high);

        List<ChatRequest> accepted = requests.lockAccepted(low, high);
        Instant now = Instant.now();
        if (!accepted.isEmpty()) {
            for (ChatRequest row : accepted) {
                row.setStatus(ChatRequestStatus.ENDED);
                row.setEndedAt(now);
                row.setEndedById(userId);
            }
            requests.saveAll(accepted);
        } else if (conversation.isPresent() && !requests.existsByPairLowIdAndPairHighId(low, high)) {
            requests.save(ChatRequest.builder()
                    .sender(users.getReferenceById(userId)).receiver(target)
                    .status(ChatRequestStatus.ENDED)
                    .pairLowId(low).pairHighId(high)
                    .respondedAt(now).endedAt(now).endedById(userId)
                    .build());
        } else {
            throw ApiException.conflict("NOT_CONNECTED", "You are not connected with this user");
        }

        Long conversationId = conversation.map(Conversation::getId).orElse(null);
        Long requestId = accepted.isEmpty() ? null : accepted.get(0).getId();
        AfterCommit.run(() -> {
            Instant at = Instant.now();
            events.chatRequest(userId, new ChatRequestEvent(ChatRequestEvent.FRIEND_REMOVED, requestId, targetId, conversationId, at, true));
            events.chatRequest(targetId, new ChatRequestEvent(ChatRequestEvent.FRIEND_REMOVED, requestId, userId, conversationId, at, false));
            notificationDispatcher.onConnectionEnded(targetId, userId, conversationId);
        });
        return new ChatRequestStatusResponse(ChatRequestStatusResponse.ENDED, null, conversationId, null);
    }

    public List<ChatRequestResponse> received(Long userId) {
        return requests.findReceivedPending(userId, PageRequest.of(0, LIST_LIMIT)).stream().map(ChatRequestResponse::of).toList();
    }

    public List<ChatRequestResponse> sent(Long userId) {
        return requests.findSent(userId, PageRequest.of(0, LIST_LIMIT)).stream().map(ChatRequestResponse::of).toList();
    }

    public long receivedCount(Long userId) {
        return requests.countByReceiverIdAndStatus(userId, ChatRequestStatus.PENDING);
    }

    public ChatRequestStatusResponse relationshipFor(Long userId, Long otherUserId) {
        if (userId.equals(otherUserId)) {
            return ChatRequestStatusResponse.of(ChatRequestStatusResponse.NONE);
        }
        Long low = Math.min(userId, otherUserId);
        Long high = Math.max(userId, otherUserId);
        Optional<Conversation> conversation = conversations.findByUserOneIdAndUserTwoId(low, high);
        boolean active = conversation.isPresent() && permissions.isMessagingAllowed(userId, otherUserId);
        if (active) {
            return new ChatRequestStatusResponse(ChatRequestStatusResponse.CONNECTED, null, conversation.get().getId(), null);
        }
        Long historyConversationId = conversation.map(Conversation::getId).orElse(null);
        Optional<ChatRequest> pending = requests.findPending(ChatRequest.pairKey(userId, otherUserId));
        if (pending.isPresent()) {
            boolean mine = pending.get().getSender().getId().equals(userId);
            return new ChatRequestStatusResponse(mine ? ChatRequestStatusResponse.OUTGOING_PENDING : ChatRequestStatusResponse.INCOMING_PENDING,
                    pending.get().getId(), historyConversationId, null);
        }
        Optional<ChatRequest> declined = requests.findFirstBySenderIdAndReceiverIdAndStatusOrderByIdDesc(userId, otherUserId, ChatRequestStatus.DECLINED);
        if (declined.isPresent() && retryAt(declined.get()).isAfter(Instant.now())) {
            return new ChatRequestStatusResponse(ChatRequestStatusResponse.DECLINED, declined.get().getId(), historyConversationId, retryAt(declined.get()));
        }
        if (historyConversationId != null) {
            return new ChatRequestStatusResponse(ChatRequestStatusResponse.ENDED, null, historyConversationId, null);
        }
        return ChatRequestStatusResponse.of(ChatRequestStatusResponse.NONE);
    }

    @Transactional(readOnly = true)
    public ChatRequestStatusResponse status(Long userId, Long otherUserId) {
        users.findById(otherUserId).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
        return relationshipFor(userId, otherUserId);
    }

    private Instant retryAt(ChatRequest declined) {
        Instant base = declined.getRespondedAt() != null ? declined.getRespondedAt() : declined.getUpdatedAt();
        return base.plus(Duration.ofHours(cooldownHours));
    }

    private ChatRequestResult result(Long viewerId, ChatRequest request) {
        Long other = request.getSender().getId().equals(viewerId) ? request.getReceiver().getId() : request.getSender().getId();
        return new ChatRequestResult(ChatRequestResponse.of(request), relationshipFor(viewerId, other), null);
    }

    private ChatRequestResult acceptedResult(Long viewerId, ChatRequest request) {
        Conversation c = conversations.findByUserOneIdAndUserTwoId(request.getPairLowId(), request.getPairHighId())
                .flatMap(found -> conversations.findWithUsersById(found.getId()))
                .orElseThrow(() -> ApiException.notFound("CONVERSATION_NOT_FOUND", "Conversation not found"));
        return new ChatRequestResult(ChatRequestResponse.of(request),
                new ChatRequestStatusResponse(ChatRequestStatusResponse.CONNECTED, null, c.getId(), null),
                ConversationResponse.of(c, viewerId));
    }

    private ChatRequestResult connected(Long viewerId, Conversation conversation) {
        Conversation full = conversations.findWithUsersById(conversation.getId()).orElse(conversation);
        return new ChatRequestResult(null,
                new ChatRequestStatusResponse(ChatRequestStatusResponse.CONNECTED, null, full.getId(), null),
                ConversationResponse.of(full, viewerId));
    }
}
