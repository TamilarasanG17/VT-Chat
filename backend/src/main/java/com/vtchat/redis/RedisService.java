package com.vtchat.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtchat.config.ScaleProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
public class RedisService {

    private static final long LOG_EVERY_MS = 30_000;

    private final StringRedisTemplate template;
    private final ObjectMapper mapper;
    private final boolean enabled;

    private final AtomicLong lastFailureLog = new AtomicLong(0);
    private volatile boolean healthy = true;

    public RedisService(StringRedisTemplate template, ObjectMapper mapper, ScaleProperties scale) {
        this.template = template;
        this.mapper = mapper;
        this.enabled = scale.getRedis().isEnabled();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isHealthy() {
        return enabled && healthy;
    }

    public boolean ping() {
        if (!enabled) {
            return false;
        }
        try {
            String pong = template.execute((org.springframework.data.redis.core.RedisCallback<String>) c -> c.ping());
            ok();
            return "PONG".equalsIgnoreCase(pong);
        } catch (RuntimeException e) {
            failed("ping", e);
            return false;
        }
    }

    public Optional<String> get(String key) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            String v = template.opsForValue().get(key);
            ok();
            return Optional.ofNullable(v);
        } catch (RuntimeException e) {
            failed("get", e);
            return Optional.empty();
        }
    }

    public boolean set(String key, String value, Duration ttl) {
        if (!enabled) {
            return false;
        }
        try {
            template.opsForValue().set(key, value, ttl);
            ok();
            return true;
        } catch (RuntimeException e) {
            failed("set", e);
            return false;
        }
    }

    public Optional<Boolean> setIfAbsent(String key, String value, Duration ttl) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            Boolean r = template.opsForValue().setIfAbsent(key, value, ttl);
            ok();
            return Optional.of(Boolean.TRUE.equals(r));
        } catch (RuntimeException e) {
            failed("setIfAbsent", e);
            return Optional.empty();
        }
    }

    public <T> Optional<T> getJson(String key, Class<T> type) {
        return get(key).flatMap(json -> {
            try {
                return Optional.of(mapper.readValue(json, type));
            } catch (Exception e) {
                delete(key);
                return Optional.empty();
            }
        });
    }

    public <T> Optional<T> getJson(String key, com.fasterxml.jackson.core.type.TypeReference<T> type) {
        return get(key).flatMap(json -> {
            try {
                return Optional.of(mapper.readValue(json, type));
            } catch (Exception e) {
                delete(key);
                return Optional.empty();
            }
        });
    }

    public boolean setJson(String key, Object value, Duration ttl) {
        try {
            return set(key, mapper.writeValueAsString(value), ttl);
        } catch (Exception e) {
            log.warn("Could not serialize a cache value: {}", e.getClass().getSimpleName());
            return false;
        }
    }

    public boolean delete(String... keys) {
        if (!enabled || keys.length == 0) {
            return false;
        }
        try {
            template.delete(List.of(keys));
            ok();
            return true;
        } catch (RuntimeException e) {
            failed("delete", e);
            return false;
        }
    }

    public boolean exists(String key) {
        return existsStrict(key).orElse(false);
    }

    public Optional<Boolean> existsStrict(String key) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            Boolean b = template.hasKey(key);
            ok();
            return Optional.of(Boolean.TRUE.equals(b));
        } catch (RuntimeException e) {
            failed("exists", e);
            return Optional.empty();
        }
    }

    public boolean expire(String key, Duration ttl) {
        if (!enabled) {
            return false;
        }
        try {
            template.expire(key, ttl);
            ok();
            return true;
        } catch (RuntimeException e) {
            failed("expire", e);
            return false;
        }
    }

    public boolean zAdd(String key, String member, double score, Duration keyTtl) {
        if (!enabled) {
            return false;
        }
        try {
            template.opsForZSet().add(key, member, score);
            template.expire(key, keyTtl);
            ok();
            return true;
        } catch (RuntimeException e) {
            failed("zadd", e);
            return false;
        }
    }

    public boolean zRemove(String key, String member) {
        if (!enabled) {
            return false;
        }
        try {
            template.opsForZSet().remove(key, member);
            ok();
            return true;
        } catch (RuntimeException e) {
            failed("zrem", e);
            return false;
        }
    }

    public Optional<Long> zCountAlive(String key, long nowMillis) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            Long n = template.opsForZSet().count(key, nowMillis, Double.POSITIVE_INFINITY);
            ok();
            return Optional.of(n == null ? 0L : n);
        } catch (RuntimeException e) {
            failed("zcount", e);
            return Optional.empty();
        }
    }

    public Optional<Long> runScript(RedisScript<Long> script, List<String> keys, Object... args) {
        if (!enabled) {
            return Optional.empty();
        }
        try {
            Object[] text = java.util.Arrays.stream(args).map(String::valueOf).toArray();
            Long r = template.execute(script, keys, text);
            ok();
            return Optional.ofNullable(r);
        } catch (RuntimeException e) {
            failed("script", e);
            return Optional.empty();
        }
    }

    public boolean publish(String channel, String message) {
        if (!enabled) {
            return false;
        }
        try {
            template.convertAndSend(channel, message);
            ok();
            return true;
        } catch (RuntimeException e) {
            failed("publish", e);
            return false;
        }
    }

    private void ok() {
        if (!healthy) {
            healthy = true;
            log.info("Redis connection restored");
        }
    }

    private void failed(String operation, RuntimeException e) {
        healthy = false;
        long now = System.currentTimeMillis();
        long last = lastFailureLog.get();
        if (now - last >= LOG_EVERY_MS && lastFailureLog.compareAndSet(last, now)) {
            log.warn("Redis unavailable during '{}' ({}); using fallbacks", operation, e.getClass().getSimpleName());
        }
    }
}
