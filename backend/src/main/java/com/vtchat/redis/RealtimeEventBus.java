package com.vtchat.redis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtchat.config.ScaleProperties;
import com.vtchat.service.PresenceRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Slf4j
@Component
public class RealtimeEventBus {

    public static final Set<String> USER_QUEUES = Set.of("/queue/messages", "/queue/notifications", "/queue/call",
            "/queue/presence", "/queue/typing", "/queue/errors");
    public static final Pattern CHAT_TOPIC = Pattern.compile("^/topic/chat/\\d{1,18}$");

    private final SimpMessagingTemplate template;
    private final RedisService redis;
    private final RedisKeyService keys;
    private final ObjectMapper mapper;
    private final PresenceRegistry presence;
    private final String instanceId;

    public RealtimeEventBus(SimpMessagingTemplate template, RedisService redis, RedisKeyService keys,
                            ObjectMapper mapper, PresenceRegistry presence, ScaleProperties scale) {
        this.template = template;
        this.redis = redis;
        this.keys = keys;
        this.mapper = mapper;
        this.presence = presence;
        this.instanceId = scale.getInstanceId();
    }

    public String instanceId() {
        return instanceId;
    }

    public void toUser(DistributedEvent.Kind kind, Long userId, String destination, Object payload) {
        template.convertAndSendToUser(String.valueOf(userId), destination, payload);
        publish(kind, DistributedEvent.Target.USER, userId, destination, payload, null);
    }

    public void toTopic(DistributedEvent.Kind kind, String destination, Object payload) {
        template.convertAndSend(destination, payload);
        publish(kind, DistributedEvent.Target.TOPIC, null, destination, payload, null);
    }

    public boolean sendCallCommand(String ownerInstance, String action, Long actorId, Object payload) {
        return publish(DistributedEvent.Kind.CALL_COMMAND, DistributedEvent.Target.USER, actorId, ownerInstance, payload, action);
    }

    void deliverRemote(DistributedEvent event) {
        if (event.target() == DistributedEvent.Target.TOPIC) {
            template.convertAndSend(event.destination(), (Object) event.payload());
            return;
        }
        if (!presence.hasLocalSession(event.userId())) {
            log.debug("WebSocket routing: user {} has no session on {}, event {} ignored", event.userId(), instanceId, event.eventId());
            return;
        }
        template.convertAndSendToUser(String.valueOf(event.userId()), event.destination(), (Object) event.payload());
    }

    private boolean publish(DistributedEvent.Kind kind, DistributedEvent.Target target, Long userId, String destination,
                            Object payload, String action) {
        if (!redis.isEnabled()) {
            return false;
        }
        try {
            JsonNode body = payload instanceof JsonNode node ? node : mapper.valueToTree(payload);
            DistributedEvent event = new DistributedEvent(UUID.randomUUID().toString(), kind, instanceId, target, userId,
                    destination, body, action, Instant.now());
            boolean sent = redis.publish(keys.channel(kind), mapper.writeValueAsString(event));
            if (!sent) {
                log.warn("Redis Pub/Sub publish failed ({}): users on other instances may miss this event", kind);
            }
            return sent;
        } catch (Exception e) {
            log.warn("Could not publish a {} event: {}", kind, e.getClass().getSimpleName());
            return false;
        }
    }
}
