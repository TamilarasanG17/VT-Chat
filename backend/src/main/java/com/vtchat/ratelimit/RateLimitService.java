package com.vtchat.ratelimit;

import com.vtchat.config.RateLimitProperties;
import com.vtchat.exception.ApiException;
import com.vtchat.redis.RedisKeyService;
import com.vtchat.redis.RedisService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
public class RateLimitService {

    public record Decision(boolean allowed, int retryAfterSeconds) {
        static Decision ok() {
            return new Decision(true, 0);
        }
    }

    private static final DefaultRedisScript<Long> WINDOW = new DefaultRedisScript<>("""
            local c = redis.call('INCR', KEYS[1])
            if c == 1 then redis.call('EXPIRE', KEYS[1], ARGV[1]) end
            local ttl = redis.call('TTL', KEYS[1])
            if ttl < 0 then
              redis.call('EXPIRE', KEYS[1], ARGV[1])
              ttl = tonumber(ARGV[1])
            end
            return c * 100000 + ttl
            """, Long.class);

    private static final class LocalWindow {
        final AtomicLong count = new AtomicLong();
        final long resetAtMillis;

        LocalWindow(long resetAtMillis) {
            this.resetAtMillis = resetAtMillis;
        }
    }

    private final RateLimitProperties props;
    private final RedisService redis;
    private final RedisKeyService keys;
    private final Map<String, LocalWindow> local = new ConcurrentHashMap<>();

    public RateLimitService(RateLimitProperties props, RedisService redis, RedisKeyService keys) {
        this.props = props;
        this.redis = redis;
        this.keys = keys;
    }

    public boolean isEnabled() {
        return props.isEnabled();
    }

    public Decision check(RateLimitBucket bucket, String subject) {
        if (!props.isEnabled()) {
            return Decision.ok();
        }
        RateLimitProperties.Rule rule = bucket.ruleFrom(props);
        if (rule.getLimit() <= 0 || rule.getWindowSeconds() <= 0) {
            return Decision.ok();
        }
        String key = keys.rateLimit(bucket.keyName(), subject);
        Optional<Long> packed = redis.runScript(WINDOW, List.of(key), rule.getWindowSeconds());
        Decision decision = packed.map(v -> decide(v / 100000, (int) (v % 100000), rule)).orElseGet(() -> checkLocal(key, rule));
        if (!decision.allowed()) {
            log.warn("Rate limit exceeded: bucket={} retryAfter={}s", bucket.keyName(), decision.retryAfterSeconds());
        }
        return decision;
    }

    public void enforce(RateLimitBucket bucket, String subject) {
        Decision d = check(bucket, subject);
        if (!d.allowed()) {
            throw tooMany(d.retryAfterSeconds());
        }
    }

    public static ApiException tooMany(int retryAfterSeconds) {
        return ApiException.tooManyRequests("RATE_LIMITED", "Too many requests. Please try again later.", Math.max(1, retryAfterSeconds));
    }

    private static Decision decide(long count, int ttl, RateLimitProperties.Rule rule) {
        return count <= rule.getLimit() ? Decision.ok() : new Decision(false, Math.max(1, ttl));
    }

    private Decision checkLocal(String key, RateLimitProperties.Rule rule) {
        long now = System.currentTimeMillis();
        LocalWindow w = local.compute(key, (k, old) ->
                old == null || old.resetAtMillis <= now ? new LocalWindow(now + rule.getWindowSeconds() * 1000L) : old);
        long count = w.count.incrementAndGet();
        if (count <= rule.getLimit()) {
            return Decision.ok();
        }
        return new Decision(false, (int) Math.max(1, (w.resetAtMillis - now + 999) / 1000));
    }

    @Scheduled(fixedDelay = 60_000)
    void purgeLocal() {
        long now = System.currentTimeMillis();
        local.entrySet().removeIf(e -> e.getValue().resetAtMillis <= now);
    }
}
