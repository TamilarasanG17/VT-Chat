package com.vtchat.service;

import com.vtchat.config.AppProperties;
import com.vtchat.dto.presence.TypingEvent;
import com.vtchat.dto.presence.TypingRequest;
import com.vtchat.entity.Conversation;
import com.vtchat.exception.ApiException;
import com.vtchat.repository.ConversationRepository;
import com.vtchat.websocket.PresenceEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class TypingService {

    private static final long MIN_REPEAT_MS = 1000;
    private static final int MAX_CACHE = 20_000;

    private record Active(Long userId, Long conversationId, Long peerId, long lastAtMs, long lastSentMs) {
    }

    private final ConversationRepository conversations;
    private final PresenceEventPublisher publisher;
    private final ConversationMetaCache metaCache;
    private final long ttlMs;

    private final Map<String, Active> active = new ConcurrentHashMap<>();
    private final Map<String, Long> peerCache = new ConcurrentHashMap<>();

    public TypingService(ConversationRepository conversations, PresenceEventPublisher publisher, AppProperties props,
                         ConversationMetaCache metaCache) {
        this.conversations = conversations;
        this.metaCache = metaCache;
        this.publisher = publisher;
        this.ttlMs = Math.max(2, props.getPresence().getTypingTtlSeconds()) * 1000L;
    }

    public void typing(Long userId, TypingRequest request) {
        if (request == null || request.conversationId() == null) {
            throw ApiException.badRequest("INVALID_TYPING", "Conversation is required");
        }
        Long conversationId = request.conversationId();
        Long peerId = peerOf(userId, conversationId);
        String key = key(userId, conversationId);
        long now = System.currentTimeMillis();

        if (request.typing()) {
            Active previous = active.get(key);
            if (previous != null && now - previous.lastSentMs() < MIN_REPEAT_MS) {
                active.put(key, new Active(userId, conversationId, peerId, now, previous.lastSentMs()));
                return;
            }
            active.put(key, new Active(userId, conversationId, peerId, now, now));
            publisher.typing(peerId, new TypingEvent(conversationId, userId, true));
        } else if (active.remove(key) != null) {
            publisher.typing(peerId, new TypingEvent(conversationId, userId, false));
        }
    }

    public void stopForSend(Long userId, Long conversationId) {
        Active removed = active.remove(key(userId, conversationId));
        if (removed != null) {
            publisher.typing(removed.peerId(), new TypingEvent(conversationId, userId, false));
        }
    }

    public void clearFor(Long userId) {
        for (Iterator<Map.Entry<String, Active>> it = active.entrySet().iterator(); it.hasNext(); ) {
            Active a = it.next().getValue();
            if (a.userId().equals(userId)) {
                it.remove();
                publisher.typing(a.peerId(), new TypingEvent(a.conversationId(), userId, false));
            }
        }
    }

    @Scheduled(fixedDelay = 3000)
    public void expire() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<String, Active>> it = active.entrySet().iterator(); it.hasNext(); ) {
            Active a = it.next().getValue();
            if (now - a.lastAtMs() > ttlMs) {
                it.remove();
                publisher.typing(a.peerId(), new TypingEvent(a.conversationId(), a.userId(), false));
            }
        }
    }

    private Long peerOf(Long userId, Long conversationId) {
        String cacheKey = key(userId, conversationId);
        Long cached = peerCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        ConversationMetaCache.Meta meta = metaCache.find(conversationId)
                .orElseThrow(() -> ApiException.notFound("CONVERSATION_NOT_FOUND", "Conversation not found"));
        if (!meta.includes(userId)) {
            throw ApiException.forbidden("FORBIDDEN", "You do not have access to this conversation");
        }
        Long peer = meta.other(userId);
        if (peerCache.size() >= MAX_CACHE) {
            peerCache.clear();
        }
        peerCache.put(cacheKey, peer);
        return peer;
    }

    private static String key(Long userId, Long conversationId) {
        return userId + ":" + conversationId;
    }
}
