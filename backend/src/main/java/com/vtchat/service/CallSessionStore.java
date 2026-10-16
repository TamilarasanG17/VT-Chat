package com.vtchat.service;

import com.vtchat.dto.call.CallHistoryItem;
import com.vtchat.dto.call.CallHistoryPage;
import com.vtchat.dto.call.CallParty;
import com.vtchat.dto.call.CallResponse;
import com.vtchat.dto.message.MessageResponse;
import com.vtchat.entity.CallSession;
import com.vtchat.entity.CallStatus;
import com.vtchat.entity.CallType;
import com.vtchat.entity.Conversation;
import com.vtchat.exception.ApiException;
import com.vtchat.repository.CallSessionRepository;
import com.vtchat.repository.ConversationRepository;
import com.vtchat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, noRollbackFor = ApiException.class)
public class CallSessionStore {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;

    public record Participants(Long conversationId, CallParty caller, CallParty receiver) {
    }

    private final CallSessionRepository calls;
    private final ConversationRepository conversations;
    private final UserRepository users;
    private final MessageService messageService;
    private final ChatPermissionService permissions;

    public Participants participants(Long conversationId, Long callerId) {
        if (conversationId == null) {
            throw ApiException.badRequest("INVALID_CALL", "Conversation is required");
        }
        Conversation conversation = conversations.findWithUsersById(conversationId)
                .orElseThrow(() -> ApiException.notFound("CONVERSATION_NOT_FOUND", "Conversation not found"));
        if (!conversation.includes(callerId)) {
            throw ApiException.forbidden("FORBIDDEN", "You do not have access to this conversation");
        }
        permissions.requireMessagingAllowed(conversation);
        return new Participants(conversation.getId(), CallParty.from(userOf(conversation, callerId)),
                CallParty.from(conversation.otherParticipant(callerId)));
    }

    private static com.vtchat.entity.User userOf(Conversation c, Long id) {
        return id.equals(c.getUserOne().getId()) ? c.getUserOne() : c.getUserTwo();
    }

    public boolean exists(String callId) {
        return calls.existsByCallId(callId);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public CallResponse open(String callId, Long conversationId, Long callerId, Long receiverId, CallType type, Instant startedAt) {
        return CallResponse.from(calls.saveAndFlush(CallSession.builder()
                .callId(callId)
                .conversation(conversations.getReferenceById(conversationId))
                .caller(users.getReferenceById(callerId))
                .receiver(users.getReferenceById(receiverId))
                .callType(type)
                .status(CallStatus.RINGING)
                .startedAt(startedAt)
                .build()));
    }

    @Transactional
    public void markAccepted(String callId, Instant at) {
        calls.findByCallIdWithUsers(callId).ifPresent(c -> {
            c.setAnsweredAt(at);
        });
    }

    @Transactional
    public void markConnected(String callId, Instant at) {
        calls.findByCallIdWithUsers(callId).ifPresent(c -> {
            c.setStatus(CallStatus.CONNECTED);
            c.setConnectedAt(at);
        });
    }

    @Transactional
    public MessageResponse recordBusy(String callId, Long conversationId, Long callerId, Long receiverId, CallType type, Instant now) {
        calls.save(CallSession.builder()
                .callId(callId)
                .conversation(conversations.getReferenceById(conversationId))
                .caller(users.getReferenceById(callerId))
                .receiver(users.getReferenceById(receiverId))
                .callType(type)
                .status(CallStatus.MISSED)
                .startedAt(now)
                .endedAt(now)
                .duration(0)
                .build());
        return messageService.recordCall(conversationId, callerId, type.recordType(), callId, CallStatus.MISSED.name(), 0);
    }

    @Transactional
    public MessageResponse finish(String callId, CallStatus status, Instant endedAt, int durationSeconds) {
        CallSession call = calls.findByCallIdWithUsers(callId).orElse(null);
        if (call == null) {
            return null;
        }
        call.setStatus(status);
        call.setEndedAt(endedAt);
        call.setDuration(durationSeconds);
        return messageService.recordCall(call.getConversation().getId(), call.getCaller().getId(),
                call.getCallType().recordType(), callId, status.name(), durationSeconds);
    }

    public CallHistoryPage history(Long userId, Long conversationId, Integer page, Integer size) {
        participants(conversationId, userId);
        return CallHistoryPage.of(calls.findForConversation(conversationId, pageable(page, size)).map(c -> CallHistoryItem.from(c, userId)));
    }

    public CallHistoryPage historyOfUser(Long userId, Integer page, Integer size) {
        return CallHistoryPage.of(calls.findForUser(userId, pageable(page, size)).map(c -> CallHistoryItem.from(c, userId)));
    }

    private static Pageable pageable(Integer page, Integer size) {
        int p = page == null ? 0 : Math.max(0, Math.min(page, 100_000));
        int s = size == null ? DEFAULT_PAGE_SIZE : Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        return PageRequest.of(p, s);
    }

    public CallHistoryItem get(Long userId, String callId) {
        Optional<CallSession> found = calls.findByCallIdWithUsers(callId == null ? "" : callId);
        return found
                .filter(c -> userId.equals(c.getCaller().getId()) || userId.equals(c.getReceiver().getId()))
                .map(c -> CallHistoryItem.from(c, userId))
                .orElseThrow(() -> ApiException.notFound("CALL_NOT_FOUND", "Call not found"));
    }

    @Transactional
    public int failAllActive(Instant now) {
        return calls.failAllActive(now);
    }

    @Transactional
    public int failOrphans(Instant now, java.util.function.Function<String, Optional<Boolean>> owner) {
        int n = 0;
        for (String callId : calls.findActiveCallIds()) {
            Optional<Boolean> owned = owner.apply(callId);
            if (owned.isEmpty()) {
                return 0;
            }
            if (!owned.get()) {
                n += calls.failByCallId(callId, now);
            }
        }
        return n;
    }
}
