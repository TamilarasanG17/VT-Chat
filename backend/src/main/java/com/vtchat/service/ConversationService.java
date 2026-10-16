package com.vtchat.service;

import com.vtchat.dto.conversation.ConversationResponse;
import com.vtchat.dto.conversation.ConversationSummaryResponse;
import com.vtchat.entity.Conversation;
import com.vtchat.entity.ConversationParticipantState;
import com.vtchat.entity.Message;
import com.vtchat.exception.ApiException;
import com.vtchat.repository.ConversationRepository;
import com.vtchat.repository.MessageRepository;
import com.vtchat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, noRollbackFor = ApiException.class)
public class ConversationService {

    static final Comparator<ConversationSummaryResponse> LIST_ORDER = Comparator
            .comparingInt((ConversationSummaryResponse c) -> c.archived() ? 2 : c.pinned() ? 0 : 1)
            .thenComparing(ConversationSummaryResponse::updatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(ConversationSummaryResponse::id, Comparator.reverseOrder());

    public record StartResult(ConversationResponse conversation, boolean created) {
    }

    private final ConversationRepository conversations;
    private final ConversationCreator creator;
    private final UserRepository users;
    private final MessageRepository messages;
    private final ConversationStateService stateService;
    private final ChatPermissionService permissions;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public StartResult start(Long currentUserId, Long targetUserId) {
        if (currentUserId.equals(targetUserId)) {
            throw ApiException.badRequest("SELF_CONVERSATION", "You cannot chat with yourself");
        }
        users.findById(targetUserId)
                .filter(u -> u.isEmailVerified())
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));

        Long lower = Math.min(currentUserId, targetUserId);
        Long higher = Math.max(currentUserId, targetUserId);

        Long conversationId = creator.findId(lower, higher)
                .orElseThrow(() -> ApiException.forbidden("CHAT_REQUEST_REQUIRED", "Send a chat request first. You can chat once it is accepted."));
        boolean created = false;

        stateService.undelete(currentUserId, conversationId);
        Conversation conversation = conversations.findWithUsersById(conversationId)
                .orElseThrow(() -> ApiException.notFound("CONVERSATION_NOT_FOUND", "Conversation not found"));
        return new StartResult(ConversationResponse.of(conversation, currentUserId)
                .withMessagingEnabled(permissions.isMessagingAllowed(currentUserId, conversation.otherParticipant(currentUserId).getId())), created);
    }

    public List<ConversationSummaryResponse> list(Long currentUserId) {
        List<Conversation> mine = conversations.findAllForUser(currentUserId);
        if (mine.isEmpty()) {
            return List.of();
        }
        Map<Long, Message> latest = messages
                .findLatestForConversations(mine.stream().map(Conversation::getId).toList(), currentUserId).stream()
                .collect(Collectors.toMap(m -> m.getConversation().getId(), Function.identity()));
        Map<Long, ConversationParticipantState> states = stateService.allFor(currentUserId);
        java.util.Set<Long> blockedPeers = permissions.blockedPeerIds(currentUserId);
        return mine.stream()
                .filter(c -> states.get(c.getId()) == null || !states.get(c.getId()).isDeleted())
                .map(c -> ConversationSummaryResponse.of(c, currentUserId, latest.get(c.getId()), states.get(c.getId()))
                        .withMessagingEnabled(!blockedPeers.contains(c.otherParticipant(currentUserId).getId())))
                .sorted(LIST_ORDER)
                .toList();
    }

    public ConversationResponse get(Long conversationId, Long currentUserId) {
        Conversation conversation = conversations.findWithUsersById(conversationId)
                .orElseThrow(() -> ApiException.notFound("CONVERSATION_NOT_FOUND", "Conversation not found"));
        if (!conversation.includes(currentUserId)) {
            throw ApiException.forbidden("FORBIDDEN", "You do not have access to this conversation");
        }
        return ConversationResponse.of(conversation, currentUserId)
                .withMessagingEnabled(permissions.isMessagingAllowed(currentUserId, conversation.otherParticipant(currentUserId).getId()));
    }
}
