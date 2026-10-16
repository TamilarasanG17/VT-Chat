package com.vtchat.service;

import com.vtchat.dto.conversation.ConversationStateEvent;
import com.vtchat.dto.conversation.ConversationStateResponse;
import com.vtchat.dto.notification.NotificationEvent;
import com.vtchat.websocket.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class ConversationActionService {

    private final ConversationStateService state;
    private final DeliveryService delivery;
    private final NotificationEventPublisher publisher;

    public ConversationStateResponse markRead(Long userId, Long conversationId, Long lastReadMessageId) {
        if (conversationId == null) {
            throw MessageService.validation("Conversation is required");
        }
        Long watermark = lastReadMessageId != null ? lastReadMessageId : state.latestMessageId(userId, conversationId);
        if (watermark != null) {
            delivery.markRead(userId, conversationId, watermark);
        }
        ConversationStateService.ReadResult result = state.markRead(userId, conversationId, watermark);
        if (result.stateChanged()) {
            publisher.conversationState(userId, ConversationStateEvent.of(result.state()));
        }
        if (result.notificationsChanged()) {
            publisher.notification(userId, NotificationEvent.updated(result.unreadNotifications(), conversationId));
        }
        return result.state();
    }

    public ConversationStateResponse pin(Long userId, Long conversationId, boolean pinned) {
        return publish(userId, () -> state.setPinned(userId, conversationId, pinned));
    }

    public ConversationStateResponse mute(Long userId, Long conversationId, boolean muted) {
        return publish(userId, () -> state.setMuted(userId, conversationId, muted));
    }

    public ConversationStateResponse archive(Long userId, Long conversationId, boolean archived) {
        return publish(userId, () -> state.setArchived(userId, conversationId, archived));
    }

    public ConversationStateResponse clear(Long userId, Long conversationId) {
        return publish(userId, () -> state.clear(userId, conversationId));
    }

    public ConversationStateResponse delete(Long userId, Long conversationId) {
        return publish(userId, () -> state.delete(userId, conversationId));
    }

    private ConversationStateResponse publish(Long userId, Supplier<ConversationStateResponse> change) {
        ConversationStateResponse result = change.get();
        publisher.conversationState(userId, ConversationStateEvent.of(result));
        return result;
    }
}
