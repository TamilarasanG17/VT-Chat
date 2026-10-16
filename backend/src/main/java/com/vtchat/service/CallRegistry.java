package com.vtchat.service;

import com.vtchat.entity.CallStatus;
import com.vtchat.entity.CallType;
import com.vtchat.config.ScaleProperties;
import com.vtchat.redis.RedisKeyService;
import com.vtchat.redis.RedisService;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class CallRegistry {

    public enum Registration { OK, CALLER_BUSY, RECEIVER_BUSY, DUPLICATE }

    public enum Phase { RINGING, ACCEPTED, CONNECTED }

    public static final class ActiveCall {
        public final String callId;
        public final Long conversationId;
        public final Long callerId;
        public final Long receiverId;
        public final CallType type;
        public final Instant startedAt = Instant.now();
        public Phase phase = Phase.RINGING;
        public Instant answeredAt;
        public Instant connectedAt;
        public boolean closed;

        public ActiveCall(String callId, Long conversationId, Long callerId, Long receiverId, CallType type) {
            this.callId = callId;
            this.conversationId = conversationId;
            this.callerId = callerId;
            this.receiverId = receiverId;
            this.type = type;
        }

        public CallStatus visibleStatus() {
            return phase == Phase.CONNECTED ? CallStatus.CONNECTED : CallStatus.RINGING;
        }

        public boolean involves(Long userId) {
            return callerId.equals(userId) || receiverId.equals(userId);
        }

        public Long other(Long userId) {
            return callerId.equals(userId) ? receiverId : callerId;
        }
    }

    private static final DefaultRedisScript<Long> CLAIM = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[3]) == 1 then return 3 end
            if redis.call('EXISTS', KEYS[1]) == 1 then return 1 end
            if redis.call('EXISTS', KEYS[2]) == 1 then return 2 end
            redis.call('SET', KEYS[1], ARGV[1], 'EX', ARGV[3])
            redis.call('SET', KEYS[2], ARGV[1], 'EX', ARGV[3])
            redis.call('SET', KEYS[3], ARGV[2], 'EX', ARGV[3])
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) == ARGV[1] then redis.call('DEL', KEYS[1]) end
            if redis.call('GET', KEYS[2]) == ARGV[1] then redis.call('DEL', KEYS[2]) end
            redis.call('DEL', KEYS[3])
            return 1
            """, Long.class);

    private static final Duration KEY_TTL = Duration.ofSeconds(60);

    private final Map<String, ActiveCall> calls = new ConcurrentHashMap<>();
    private final Map<Long, String> callOfUser = new ConcurrentHashMap<>();

    private final RedisService redis;
    private final RedisKeyService keys;
    private final String instanceId;

    public CallRegistry(RedisService redis, RedisKeyService keys, ScaleProperties scale) {
        this.redis = redis;
        this.keys = keys;
        this.instanceId = scale.getInstanceId();
    }

    public Registration register(ActiveCall call) {
        Optional<Long> claim = redis.runScript(CLAIM,
                java.util.List.of(keys.callOfUser(call.callerId), keys.callOfUser(call.receiverId), keys.callOwner(call.callId)),
                call.callId, instanceId, KEY_TTL.toSeconds());
        if (claim.isPresent() && claim.get() != 0) {
            return switch (claim.get().intValue()) {
                case 1 -> Registration.CALLER_BUSY;
                case 2 -> Registration.RECEIVER_BUSY;
                default -> Registration.DUPLICATE;
            };
        }
        Registration local = registerLocal(call);
        if (local != Registration.OK && claim.isPresent()) {
            release(call);
        }
        return local;
    }

    public Optional<String> ownerOf(String callId) {
        return redis.get(keys.callOwner(callId));
    }

    public Optional<String> callIdOfUser(Long userId) {
        return redis.get(keys.callOfUser(userId));
    }

    public Optional<Boolean> hasOwner(String callId) {
        return redis.existsStrict(keys.callOwner(callId));
    }

    public void refresh(ActiveCall call) {
        redis.expire(keys.callOfUser(call.callerId), KEY_TTL);
        redis.expire(keys.callOfUser(call.receiverId), KEY_TTL);
        redis.expire(keys.callOwner(call.callId), KEY_TTL);
    }

    private void release(ActiveCall call) {
        redis.runScript(RELEASE, java.util.List.of(keys.callOfUser(call.callerId), keys.callOfUser(call.receiverId), keys.callOwner(call.callId)),
                call.callId);
    }

    private synchronized Registration registerLocal(ActiveCall call) {
        if (calls.containsKey(call.callId)) {
            return Registration.DUPLICATE;
        }
        if (callOfUser.containsKey(call.callerId)) {
            return Registration.CALLER_BUSY;
        }
        if (callOfUser.containsKey(call.receiverId)) {
            return Registration.RECEIVER_BUSY;
        }
        calls.put(call.callId, call);
        callOfUser.put(call.callerId, call.callId);
        callOfUser.put(call.receiverId, call.callId);
        return Registration.OK;
    }

    public ActiveCall get(String callId) {
        return callId == null ? null : calls.get(callId);
    }

    public void remove(ActiveCall call) {
        synchronized (this) {
            calls.remove(call.callId, call);
            callOfUser.remove(call.callerId, call.callId);
            callOfUser.remove(call.receiverId, call.callId);
        }
        release(call);
    }

    public ActiveCall forUser(Long userId) {
        String id = callOfUser.get(userId);
        return id == null ? null : calls.get(id);
    }

    public List<ActiveCall> all() {
        return new ArrayList<>(calls.values());
    }
}
