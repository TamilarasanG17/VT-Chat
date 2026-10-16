package com.vtchat.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 30, message = "Username must be 3-30 characters")
        @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "Username may only contain letters, numbers, dots, dashes and underscores")
        String username,

        @Size(max = 160, message = "Bio must be 160 characters or fewer")
        String bio) {

    public UpdateProfileRequest {
        username = username == null ? null : username.trim();
        if (bio != null) {
            bio = bio.replace("\r\n", "\n").replaceAll("[\\p{Cc}&&[^\\n]]", "").strip();
            if (bio.isEmpty()) {
                bio = null;
            }
        }
    }
}
