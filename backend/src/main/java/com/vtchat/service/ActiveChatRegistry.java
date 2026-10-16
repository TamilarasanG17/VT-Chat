package com.vtchat.service;

import com.vtchat.config.ScaleProperties;
import com.vtchat.redis.RedisKeyService;
import com.vtchat.redis.RedisService;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class ActiveChatRegistry {

    private record Entry(Long userId, Long conversationId) {
    }

    private final Map<String, Entry> bySession = new HashMap<>();

    private final RedisService redis;
    private final RedisKeyService keys;
    private final String instanceId;
    private final long ttlMillis;
    private final Duration keyTtl;

    public ActiveChatRegistry(RedisService redis, RedisKeyService keys, ScaleProperties scale) {
        this.redis = redis;
        this.keys = keys;
        this.instanceId = scale.getInstanceId();
        this.ttlMillis = scale.getRedis().getSessionTtlSeconds() * 1000L;
        this.keyTtl = Duration.ofSeconds(scale.getRedis().getSessionTtlSeconds() * 2);
    }

    public void set(String sessionId, Long userId, Long conversationId) {
        if (sessionId == null || userId == null) {
            return;
        }
        Entry previous;
        synchronized (this) {
            previous = conversationId == null
                    ? bySession.remove(sessionId)
                    : bySession.put(sessionId, new Entry(userId, conversationId));
        }
        if (previous != null && !previous.conversationId().equals(conversationId)) {
            redis.zRemove(keys.activeChat(previous.userId(), previous.conversationId()), member(sessionId));
        }
        if (conversationId != null) {
            redis.zAdd(keys.activeChat(userId, conversationId), member(sessionId), System.currentTimeMillis() + ttlMillis, keyTtl);
        }
    }

    public boolean isActive(Long userId, Long conversationId) {
        synchronized (this) {
            if (bySession.values().stream().anyMatch(e -> e.userId().equals(userId) && e.conversationId().equals(conversationId))) {
                return true;
            }
        }
        return redis.zCountAlive(keys.activeChat(userId, conversationId), System.currentTimeMillis()).map(n -> n > 0).orElse(false);
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        Entry removed;
        synchronized (this) {
            removed = bySession.remove(event.getSessionId());
        }
        if (removed != null) {
            redis.zRemove(keys.activeChat(removed.userId(), removed.conversationId()), member(event.getSessionId()));
        }
    }

    @Scheduled(fixedDelayString = "#{${vtchat.redis.heartbeat-seconds:30} * 1000}", initialDelayString = "#{${vtchat.redis.heartbeat-seconds:30} * 1000}")
    public void heartbeat() {
        if (!redis.isEnabled()) {
            return;
        }
        List<Map.Entry<String, Entry>> snapshot;
        synchronized (this) {
            snapshot = new ArrayList<>(bySession.entrySet());
        }
        long expiry = System.currentTimeMillis() + ttlMillis;
        for (Map.Entry<String, Entry> e : snapshot) {
            redis.zAdd(keys.activeChat(e.getValue().userId(), e.getValue().conversationId()), member(e.getKey()), expiry, keyTtl);
        }
    }

    private String member(String sessionId) {
        return instanceId + ":" + sessionId;
    }
}
