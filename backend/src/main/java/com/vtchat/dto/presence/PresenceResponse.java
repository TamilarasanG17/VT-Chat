package com.vtchat.dto.presence;

import java.time.Instant;

public record PresenceResponse(Long userId, String status, Instant lastSeenAt) {

    public static final String ONLINE = "ONLINE";
    public static final String OFFLINE = "OFFLINE";
    public static final String HIDDEN = "HIDDEN";
}
