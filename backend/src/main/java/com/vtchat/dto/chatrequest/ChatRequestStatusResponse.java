package com.vtchat.dto.chatrequest;

import java.time.Instant;

public record ChatRequestStatusResponse(String relationship, Long requestId, Long conversationId, Instant retryAt) {

    public static final String NONE = "NONE";
    public static final String OUTGOING_PENDING = "OUTGOING_PENDING";
    public static final String INCOMING_PENDING = "INCOMING_PENDING";
    public static final String CONNECTED = "CONNECTED";
    public static final String DECLINED = "DECLINED";
    public static final String ENDED = "ENDED";

    public static ChatRequestStatusResponse of(String relationship) {
        return new ChatRequestStatusResponse(relationship, null, null, null);
    }
}
