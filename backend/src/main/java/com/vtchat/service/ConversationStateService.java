package com.vtchat.service;

import com.vtchat.dto.conversation.ConversationStateResponse;
import com.vtchat.entity.Conversation;
import com.vtchat.entity.ConversationParticipantState;
import com.vtchat.exception.ApiException;
import com.vtchat.repository.ConversationParticipantStateRepository;
import com.vtchat.repository.ConversationRepository;
import com.vtchat.repository.MessageDeletionRepository;
import com.vtchat.repository.MessageRepository;
import com.vtchat.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(noRollbackFor = ApiException.class)
public class ConversationStateService {

    public record ReadResult(ConversationStateResponse state, boolean stateChanged, boolean notificationsChanged,
                             long unreadNotifications) {
    }

    private final ConversationParticipantStateRepository states;
    private final ConversationRepository conversations;
    private final MessageRepository messages;
    private final MessageDeletionRepository deletions;
    private final NotificationRepository notifications;
    private final ActiveChatRegistry activeChats;
    private final UnreadCounterService counters;

    public void onIncoming(Long conversationId, Long receiverId, boolean countsAsUnread) {
        states.ensure(conversationId, receiverId);
        if (countsAsUnread && !activeChats.isActive(receiverId, conversationId)) {
            states.incrementUnread(conversationId, receiverId);
        } else {
            states.resurface(conversationId, receiverId);
        }
    }

    public void undelete(Long userId, Long conversationId) {
        states.resurface(conversationId, userId);
    }

    @Transactional(readOnly = true, noRollbackFor = ApiException.class)
    public Map<Long, ConversationParticipantState> allFor(Long userId) {
        return states.findAllForUser(userId).stream()
                .collect(Collectors.toMap(s -> s.getConversation().getId(), Function.identity()));
    }

    @Transactional(readOnly = true, noRollbackFor = ApiException.class)
    public ConversationStateResponse snapshot(Long userId, Long conversationId) {
        return states.findByConversationIdAndUserId(conversationId, userId)
                .map(s -> toResponse(conversationId, s))
                .orElseGet(() -> new ConversationStateResponse(conversationId, 0, false, false, false, false, null));
    }

    public ConversationStateResponse state(Long userId, Long conversationId) {
        requireMember(userId, conversationId);
        return snapshot(userId, conversationId);
    }

    @Transactional(readOnly = true, noRollbackFor = ApiException.class)
    public Long latestMessageId(Long userId, Long conversationId) {
        requireMember(userId, conversationId);
        return messages.findMaxId(conversationId);
    }

    public ReadResult markRead(Long userId, Long conversationId, Long watermark) {
        requireMember(userId, conversationId);
        ConversationParticipantState s = load(userId, conversationId);
        int before = s.getUnreadCount();
        Long last = s.getLastReadMessageId();
        if (watermark != null && (last == null || watermark > last)) {
            last = watermark;
            s.setLastReadMessageId(watermark);
        }
        int unread = (int) messages.countUnreadAfter(conversationId, userId, last == null ? 0L : last);
        s.setUnreadCount(unread);
        states.saveAndFlush(s);
        int marked = notifications.markConversationRead(userId, conversationId);
        if (marked > 0) {
            counters.notificationsChanged(userId);
        }
        return new ReadResult(toResponse(conversationId, s), unread != before, marked > 0, notifications.countUnread(userId));
    }

    public ConversationStateResponse setPinned(Long userId, Long conversationId, boolean pinned) {
        ConversationParticipantState s = loadForChange(userId, conversationId);
        s.setPinned(pinned);
        return save(conversationId, s);
    }

    public ConversationStateResponse setMuted(Long userId, Long conversationId, boolean muted) {
        ConversationParticipantState s = loadForChange(userId, conversationId);
        s.setMuted(muted);
        return save(conversationId, s);
    }

    public ConversationStateResponse setArchived(Long userId, Long conversationId, boolean archived) {
        ConversationParticipantState s = loadForChange(userId, conversationId);
        s.setArchived(archived);
        return save(conversationId, s);
    }

    public ConversationStateResponse clear(Long userId, Long conversationId) {
        ConversationParticipantState s = loadForChange(userId, conversationId);
        applyClear(userId, conversationId, s);
        return save(conversationId, s);
    }

    public ConversationStateResponse delete(Long userId, Long conversationId) {
        ConversationParticipantState s = loadForChange(userId, conversationId);
        applyClear(userId, conversationId, s);
        s.setDeleted(true);
        s.setPinned(false);
        s.setArchived(false);
        return save(conversationId, s);
    }

    private void applyClear(Long userId, Long conversationId, ConversationParticipantState s) {
        deletions.hideAllInConversation(conversationId, userId);
        s.setClearedAt(Instant.now());
        s.setUnreadCount(0);
        Long newest = messages.findMaxId(conversationId);
        if (newest != null) {
            s.setLastReadMessageId(newest);
        }
        notifications.markConversationRead(userId, conversationId);
        counters.notificationsChanged(userId);
    }

    private ConversationParticipantState loadForChange(Long userId, Long conversationId) {
        requireMember(userId, conversationId);
        return load(userId, conversationId);
    }

    private ConversationParticipantState load(Long userId, Long conversationId) {
        states.ensure(conversationId, userId);
        return states.findByConversationIdAndUserId(conversationId, userId)
                .orElseThrow(() -> ApiException.notFound("CONVERSATION_NOT_FOUND", "Conversation not found"));
    }

    private ConversationStateResponse save(Long conversationId, ConversationParticipantState s) {
        return toResponse(conversationId, states.saveAndFlush(s));
    }

    private void requireMember(Long userId, Long conversationId) {
        Conversation c = conversations.findWithUsersById(conversationId)
                .orElseThrow(() -> ApiException.notFound("CONVERSATION_NOT_FOUND", "Conversation not found"));
        if (!c.includes(userId)) {
            throw ApiException.forbidden("FORBIDDEN", "You do not have access to this conversation");
        }
    }

    private static ConversationStateResponse toResponse(Long conversationId, ConversationParticipantState s) {
        return new ConversationStateResponse(conversationId, s.getUnreadCount(), s.isMuted(), s.isPinned(),
                s.isArchived(), s.isDeleted(), s.getLastReadMessageId());
    }
}
