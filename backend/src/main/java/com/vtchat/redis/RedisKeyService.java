package com.vtchat.redis;

import com.vtchat.config.ScaleProperties;
import org.springframework.stereotype.Component;

@Component
public class RedisKeyService {

    private final ScaleProperties.Channels channels;

    public RedisKeyService(ScaleProperties scale) {
        this.channels = scale.getRedis().getChannels();
    }

    public String user(Long userId) {
        return "cache:user:" + userId;
    }

    public String search(Long userId, String queryHash) {
        return "cache:search:" + userId + ":" + queryHash;
    }

    public String conversation(Long conversationId) {
        return "cache:conversation:" + conversationId;
    }

    public String unreadNotifications(Long userId) {
        return "unread:user:" + userId + ":notifications";
    }

    public String wsUser(Long userId) {
        return "ws:user:" + userId;
    }

    public String activeChat(Long userId, Long conversationId) {
        return "ws:active:" + userId + ":" + conversationId;
    }

    public String callOwner(String callId) {
        return "call:owner:" + callId;
    }

    public String callOfUser(Long userId) {
        return "call:user:" + userId;
    }

    public String aiGenerating(Long userId, Long conversationId) {
        return "ai:generating:" + userId + ":" + conversationId;
    }

    public String rateLimit(String bucket, String subject) {
        return "rate-limit:" + bucket + ":" + subject;
    }

    public String channel(DistributedEvent.Kind kind) {
        return switch (kind) {
            case MESSAGE -> channels.getMessages();
            case READ_STATE -> channels.getReadState();
            case NOTIFICATION -> channels.getNotifications();
            case CALL_SIGNALING, CALL_COMMAND -> channels.getCalls();
            case PRESENCE, TYPING -> channels.getPresence();
        };
    }
}
