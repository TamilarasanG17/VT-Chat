package com.vtchat.dto.presence;

import java.time.Instant;

public record PresenceEvent(String type, Long userId, String status, Instant lastSeenAt) {

    public static PresenceEvent of(PresenceResponse p) {
        String type = switch (p.status()) {
            case PresenceResponse.ONLINE -> "PRESENCE_ONLINE";
            case PresenceResponse.HIDDEN -> "PRESENCE_HIDDEN";
            default -> "PRESENCE_OFFLINE";
        };
        return new PresenceEvent(type, p.userId(), p.status(), p.lastSeenAt());
    }
}
