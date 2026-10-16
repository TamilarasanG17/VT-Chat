package com.vtchat.websocket;

import com.vtchat.dto.presence.PresenceEvent;
import com.vtchat.dto.presence.TypingEvent;
import com.vtchat.redis.DistributedEvent.Kind;
import com.vtchat.redis.RealtimeEventBus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PresenceEventPublisher {

    private final RealtimeEventBus bus;

    public void presence(Long toUserId, PresenceEvent event) {
        bus.toUser(Kind.PRESENCE, toUserId, "/queue/presence", event);
    }

    public void typing(Long toUserId, TypingEvent event) {
        bus.toUser(Kind.TYPING, toUserId, "/queue/typing", event);
    }
}
