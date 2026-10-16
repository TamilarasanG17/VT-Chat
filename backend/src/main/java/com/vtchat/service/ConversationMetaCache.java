package com.vtchat.service;

import com.vtchat.config.ScaleProperties;
import com.vtchat.entity.Conversation;
import com.vtchat.redis.AfterCommit;
import com.vtchat.redis.RedisKeyService;
import com.vtchat.redis.RedisService;
import com.vtchat.repository.ConversationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Component
public class ConversationMetaCache {

    public record Meta(Long conversationId, Long userOneId, Long userTwoId, Instant updatedAt) {

        public boolean includes(Long userId) {
            return userId != null && (userId.equals(userOneId) || userId.equals(userTwoId));
        }

        public Long other(Long userId) {
            return userId.equals(userOneId) ? userTwoId : userOneId;
        }
    }

    private final RedisService redis;
    private final RedisKeyService keys;
    private final ConversationRepository conversations;
    private final Duration ttl;

    public ConversationMetaCache(RedisService redis, RedisKeyService keys, ConversationRepository conversations, ScaleProperties scale) {
        this.redis = redis;
        this.keys = keys;
        this.conversations = conversations;
        this.ttl = Duration.ofSeconds(scale.getRedis().getCacheTtlSeconds());
    }

    @Transactional(readOnly = true)
    public Optional<Meta> find(Long conversationId) {
        String key = keys.conversation(conversationId);
        Optional<Meta> hit = redis.getJson(key, Meta.class);
        if (hit.isPresent()) {
            return hit;
        }
        Optional<Meta> loaded = conversations.findWithUsersById(conversationId).map(ConversationMetaCache::toMeta);
        loaded.ifPresent(m -> redis.setJson(key, m, ttl));
        return loaded;
    }

    public void invalidate(Long conversationId) {
        AfterCommit.run(() -> redis.delete(keys.conversation(conversationId)));
    }

    private static Meta toMeta(Conversation c) {
        return new Meta(c.getId(), c.getUserOne().getId(), c.getUserTwo().getId(), c.getUpdatedAt());
    }
}
