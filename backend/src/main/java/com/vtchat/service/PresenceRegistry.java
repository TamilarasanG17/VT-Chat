package com.vtchat.service;

import com.vtchat.config.ScaleProperties;
import com.vtchat.redis.RedisKeyService;
import com.vtchat.redis.RedisService;
import jakarta.annotation.PreDestroy;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
public class PresenceRegistry {

    private static final DefaultRedisScript<Long> CONNECT = new DefaultRedisScript<>("""
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
            local before = redis.call('ZCARD', KEYS[1])
            redis.call('ZADD', KEYS[1], ARGV[2], ARGV[3])
            redis.call('EXPIRE', KEYS[1], ARGV[4])
            return before
            """, Long.class);

    private static final DefaultRedisScript<Long> DISCONNECT = new DefaultRedisScript<>("""
            redis.call('ZREM', KEYS[1], ARGV[2])
            redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[1])
            return redis.call('ZCARD', KEYS[1])
            """, Long.class);

    private final Map<Long, Set<String>> sessions = new HashMap<>();

    private final RedisService redis;
    private final RedisKeyService keys;
    private final String instanceId;
    private final long sessionTtlMillis;
    private final Duration keyTtl;

    public PresenceRegistry(RedisService redis, RedisKeyService keys, ScaleProperties scale) {
        this.redis = redis;
        this.keys = keys;
        this.instanceId = scale.getInstanceId();
        this.sessionTtlMillis = scale.getRedis().getSessionTtlSeconds() * 1000L;
        this.keyTtl = Duration.ofSeconds(scale.getRedis().getSessionTtlSeconds() * 2);
    }

    public boolean connect(Long userId, String sessionId) {
        boolean firstLocal;
        synchronized (this) {
            Set<String> set = sessions.computeIfAbsent(userId, id -> new HashSet<>());
            firstLocal = set.isEmpty();
            set.add(sessionId);
        }
        long now = System.currentTimeMillis();
        Optional<Long> before = redis.runScript(CONNECT, List.of(keys.wsUser(userId)), now, now + sessionTtlMillis, member(sessionId),
                keyTtl.toSeconds());
        return before.map(n -> n == 0).orElse(firstLocal);
    }

    public boolean disconnect(Long userId, String sessionId) {
        boolean emptyLocal;
        synchronized (this) {
            Set<String> set = sessions.get(userId);
            if (set != null) {
                set.remove(sessionId);
                if (set.isEmpty()) {
                    sessions.remove(userId);
                }
            }
            emptyLocal = !sessions.containsKey(userId);
        }
        Optional<Long> remaining = redis.runScript(DISCONNECT, List.of(keys.wsUser(userId)), System.currentTimeMillis(), member(sessionId));
        return remaining.map(n -> n == 0).orElse(emptyLocal);
    }

    public boolean isConnected(Long userId) {
        if (hasLocalSession(userId)) {
            return true;
        }
        return redis.zCountAlive(keys.wsUser(userId), System.currentTimeMillis()).map(n -> n > 0).orElse(false);
    }

    public int sessionCount(Long userId) {
        int local = localSessionCount(userId);
        return redis.zCountAlive(keys.wsUser(userId), System.currentTimeMillis()).map(n -> (int) Math.max(n, local)).orElse(local);
    }

    public synchronized boolean hasLocalSession(Long userId) {
        return sessions.containsKey(userId);
    }

    public synchronized int localSessionCount(Long userId) {
        Set<String> set = sessions.get(userId);
        return set == null ? 0 : set.size();
    }

    public synchronized int totalLocalSessions() {
        return sessions.values().stream().mapToInt(Set::size).sum();
    }

    @Scheduled(fixedDelayString = "#{${vtchat.redis.heartbeat-seconds:30} * 1000}", initialDelayString = "#{${vtchat.redis.heartbeat-seconds:30} * 1000}")
    public void heartbeat() {
        if (!redis.isEnabled()) {
            return;
        }
        List<Map.Entry<Long, String>> snapshot = new ArrayList<>();
        synchronized (this) {
            sessions.forEach((userId, set) -> set.forEach(s -> snapshot.add(Map.entry(userId, s))));
        }
        long expiry = System.currentTimeMillis() + sessionTtlMillis;
        for (Map.Entry<Long, String> e : snapshot) {
            redis.zAdd(keys.wsUser(e.getKey()), member(e.getValue()), expiry, keyTtl);
        }
    }

    @PreDestroy
    void removeLocalSessions() {
        List<Map.Entry<Long, String>> snapshot = new ArrayList<>();
        synchronized (this) {
            sessions.forEach((userId, set) -> set.forEach(s -> snapshot.add(Map.entry(userId, s))));
        }
        for (Map.Entry<Long, String> e : snapshot) {
            redis.zRemove(keys.wsUser(e.getKey()), member(e.getValue()));
        }
    }

    private String member(String sessionId) {
        return instanceId + ":" + sessionId;
    }
}
