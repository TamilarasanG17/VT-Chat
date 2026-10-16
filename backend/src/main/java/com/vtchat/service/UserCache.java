package com.vtchat.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.vtchat.config.ScaleProperties;
import com.vtchat.dto.user.UserResponse;
import com.vtchat.dto.user.UserSearchResponse;
import com.vtchat.redis.AfterCommit;
import com.vtchat.redis.RedisKeyService;
import com.vtchat.redis.RedisService;
import com.vtchat.util.HashUtil;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

@Component
public class UserCache {

    private final RedisService redis;
    private final RedisKeyService keys;
    private final Duration profileTtl;
    private final Duration searchTtl;

    public UserCache(RedisService redis, RedisKeyService keys, ScaleProperties scale) {
        this.redis = redis;
        this.keys = keys;
        this.profileTtl = Duration.ofSeconds(scale.getRedis().getCacheTtlSeconds());
        this.searchTtl = Duration.ofSeconds(scale.getRedis().getSearchCacheTtlSeconds());
    }

    public Optional<UserResponse> profile(Long userId, Supplier<Optional<UserResponse>> loader) {
        String key = keys.user(userId);
        Optional<UserResponse> hit = redis.getJson(key, UserResponse.class);
        if (hit.isPresent()) {
            return hit;
        }
        Optional<UserResponse> loaded = loader.get();
        loaded.ifPresent(p -> redis.setJson(key, p, profileTtl));
        return loaded;
    }

    public void invalidate(Long userId) {
        AfterCommit.run(() -> redis.delete(keys.user(userId)));
    }

    public List<UserSearchResponse> search(Long searcherId, String query, Supplier<List<UserSearchResponse>> loader) {
        String key = keys.search(searcherId, HashUtil.sha256Hex(query.toLowerCase(java.util.Locale.ROOT)));
        Optional<List<UserSearchResponse>> hit = redis.getJson(key, new TypeReference<List<UserSearchResponse>>() {
        });
        if (hit.isPresent()) {
            return hit.get();
        }
        List<UserSearchResponse> loaded = loader.get();
        redis.setJson(key, loaded, searchTtl);
        return loaded;
    }
}
