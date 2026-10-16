package com.vtchat.websocket;

import com.vtchat.dto.message.MessageEvent;
import com.vtchat.redis.DistributedEvent.Kind;
import com.vtchat.redis.RealtimeEventBus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MessageEventPublisher {

    private final RealtimeEventBus bus;

    public void toConversation(MessageEvent event) {
        bus.toTopic(Kind.MESSAGE, "/topic/chat/" + event.conversationId(), event);
    }

    public void receipt(com.vtchat.dto.message.ReceiptEvent event) {
        bus.toTopic(Kind.READ_STATE, "/topic/chat/" + event.conversationId(), event);
    }

    public void toUser(Long userId, MessageEvent event) {
        bus.toUser(Kind.MESSAGE, userId, "/queue/messages", event);
    }
}
