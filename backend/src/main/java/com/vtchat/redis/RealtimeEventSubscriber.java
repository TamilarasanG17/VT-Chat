package com.vtchat.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtchat.config.ScaleProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class RealtimeEventSubscriber implements MessageListener {

    private final ObjectMapper mapper;
    private final RealtimeEventBus bus;
    private final RedisKeyService keys;
    private final ObjectProvider<CallCommandListener> callListener;
    private final String instanceId;
    private final int maxBytes;

    public RealtimeEventSubscriber(ObjectMapper mapper, RealtimeEventBus bus, RedisKeyService keys,
                                   ObjectProvider<CallCommandListener> callListener, ScaleProperties scale) {
        this.mapper = mapper;
        this.bus = bus;
        this.keys = keys;
        this.callListener = callListener;
        this.instanceId = scale.getInstanceId();
        this.maxBytes = scale.getRedis().getMaxEventBytes();
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            byte[] body = message.getBody();
            if (body == null || body.length == 0 || body.length > maxBytes) {
                log.warn("Pub/Sub event dropped: bad size");
                return;
            }
            DistributedEvent event = mapper.readValue(body, DistributedEvent.class);
            String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
            if (!valid(event, channel)) {
                log.warn("Pub/Sub event dropped: failed validation");
                return;
            }
            if (instanceId.equals(event.origin())) {
                return;
            }
            if (event.kind() == DistributedEvent.Kind.CALL_COMMAND) {
                if (instanceId.equals(event.destination())) {
                    CallCommandListener listener = callListener.getIfAvailable();
                    if (listener != null) {
                        listener.onCallCommand(event);
                    }
                }
                return;
            }
            bus.deliverRemote(event);
        } catch (Exception e) {
            log.warn("Pub/Sub event could not be processed: {}", e.getClass().getSimpleName());
        }
    }

    private boolean valid(DistributedEvent e, String channel) {
        if (e == null || e.kind() == null || e.target() == null || e.origin() == null || e.payload() == null
                || e.destination() == null || !channel.equals(keys.channel(e.kind()))) {
            return false;
        }
        if (e.kind() == DistributedEvent.Kind.CALL_COMMAND) {
            return e.userId() != null && e.action() != null && e.destination().length() <= 64;
        }
        if (e.target() == DistributedEvent.Target.TOPIC) {
            return RealtimeEventBus.CHAT_TOPIC.matcher(e.destination()).matches()
                    && (e.kind() == DistributedEvent.Kind.MESSAGE || e.kind() == DistributedEvent.Kind.READ_STATE);
        }
        if (e.userId() == null || !RealtimeEventBus.USER_QUEUES.contains(e.destination())) {
            return false;
        }
        return switch (e.kind()) {
            case MESSAGE, READ_STATE -> e.destination().equals("/queue/messages");
            case NOTIFICATION -> e.destination().equals("/queue/notifications");
            case CALL_SIGNALING -> e.destination().equals("/queue/call") || e.destination().equals("/queue/errors");
            case PRESENCE -> e.destination().equals("/queue/presence");
            case TYPING -> e.destination().equals("/queue/typing");
            default -> false;
        };
    }
}
