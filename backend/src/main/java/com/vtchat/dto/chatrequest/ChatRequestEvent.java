package com.vtchat.dto.chatrequest;

import java.time.Instant;

public record ChatRequestEvent(String type, Long requestId, Long otherUserId, Long conversationId, Instant at, boolean byMe) {

    public static final String RECEIVED = "CHAT_REQUEST_RECEIVED";
    public static final String ACCEPTED = "CHAT_REQUEST_ACCEPTED";
    public static final String DECLINED = "CHAT_REQUEST_DECLINED";
    public static final String FRIEND_REMOVED = "FRIEND_REMOVED";

    public ChatRequestEvent(String type, Long requestId, Long otherUserId, Long conversationId) {
        this(type, requestId, otherUserId, conversationId, Instant.now(), false);
    }
}
