package com.vtchat.websocket;

import com.vtchat.dto.call.CallEvent;
import com.vtchat.redis.DistributedEvent.Kind;
import com.vtchat.redis.RealtimeEventBus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CallEventPublisher {

    private final RealtimeEventBus bus;

    public void toUser(Long userId, CallEvent event) {
        bus.toUser(Kind.CALL_SIGNALING, userId, "/queue/call", event);
    }
}
