package com.vtchat.redis;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

public record DistributedEvent(String eventId, Kind kind, String origin, Target target, Long userId,
                               String destination, JsonNode payload, String action, Instant createdAt) {

    public enum Kind {
        MESSAGE,
        READ_STATE,
        NOTIFICATION,
        CALL_SIGNALING,
        CALL_COMMAND,
        PRESENCE,
        TYPING
    }

    public enum Target { USER, TOPIC }
}
