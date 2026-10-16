package com.vtchat.websocket;

import com.vtchat.dto.conversation.ConversationStateEvent;
import com.vtchat.dto.notification.NotificationEvent;
import com.vtchat.redis.DistributedEvent.Kind;
import com.vtchat.redis.RealtimeEventBus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class NotificationEventPublisher {

    private final RealtimeEventBus bus;

    public void notification(Long userId, NotificationEvent event) {
        bus.toUser(Kind.NOTIFICATION, userId, "/queue/notifications", event);
    }

    public void conversationState(Long userId, ConversationStateEvent event) {
        bus.toUser(Kind.READ_STATE, userId, "/queue/messages", event);
    }

    public void chatRequest(Long userId, com.vtchat.dto.chatrequest.ChatRequestEvent event) {
        bus.toUser(Kind.READ_STATE, userId, "/queue/messages", event);
    }
}
