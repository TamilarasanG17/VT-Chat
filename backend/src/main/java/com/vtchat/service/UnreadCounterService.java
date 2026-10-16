package com.vtchat.service;

import com.vtchat.config.ScaleProperties;
import com.vtchat.redis.AfterCommit;
import com.vtchat.redis.RedisKeyService;
import com.vtchat.redis.RedisService;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.LongSupplier;

@Component
public class UnreadCounterService {

    private static final DefaultRedisScript<Long> INCREMENT_IF_PRESENT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then
              local v = redis.call('INCR', KEYS[1])
              redis.call('EXPIRE', KEYS[1], ARGV[1])
              return v
            end
            return -1
            """, Long.class);

    private final RedisService redis;
    private final RedisKeyService keys;
    private final Duration ttl;

    public UnreadCounterService(RedisService redis, RedisKeyService keys, ScaleProperties scale) {
        this.redis = redis;
        this.keys = keys;
        this.ttl = Duration.ofSeconds(scale.getRedis().getUnreadTtlSeconds());
    }

    public long notificationCount(Long userId, LongSupplier loader) {
        String key = keys.unreadNotifications(userId);
        Optional<Long> cached = redis.get(key).flatMap(UnreadCounterService::parse);
        if (cached.isPresent()) {
            return cached.get();
        }
        long fresh = loader.getAsLong();
        redis.set(key, Long.toString(fresh), ttl);
        return fresh;
    }

    public void notificationCreated(Long userId) {
        AfterCommit.run(() -> redis.runScript(INCREMENT_IF_PRESENT, List.of(keys.unreadNotifications(userId)), ttl.toSeconds()));
    }

    public void notificationsChanged(Long userId) {
        AfterCommit.run(() -> redis.delete(keys.unreadNotifications(userId)));
    }

    private static Optional<Long> parse(String value) {
        try {
            long n = Long.parseLong(value);
            return n >= 0 ? Optional.of(n) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
