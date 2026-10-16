package com.vtchat.dto.presence;

import jakarta.validation.constraints.NotNull;

public record PrivacyRequest(@NotNull(message = "showOnlineStatus is required") Boolean showOnlineStatus,
                             @NotNull(message = "showLastSeen is required") Boolean showLastSeen) {
}
