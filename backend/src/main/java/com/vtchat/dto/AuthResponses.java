package com.vtchat.dto;

import com.vtchat.dto.user.UserResponse;

public final class AuthResponses {

    private AuthResponses() {
    }

    public record OtpSent(String email,
                          String purpose,
                          String nextStep,
                          long expiresInSeconds,
                          long resendCooldownSeconds) {
    }

    public record Session(String accessToken, String tokenType, long expiresIn, UserResponse user) {
    }

    public record ResetToken(String resetToken, long expiresInSeconds) {
    }
}
