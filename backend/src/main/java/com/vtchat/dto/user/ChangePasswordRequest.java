package com.vtchat.dto.user;

import com.vtchat.dto.AuthRequest;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ChangePasswordRequest(
        @NotBlank(message = "Current password is required")
        @Size(max = 128, message = "Password is too long")
        String currentPassword,

        @NotBlank(message = "New password is required")
        @Pattern(regexp = AuthRequest.PASSWORD_REGEX, message = AuthRequest.PASSWORD_MESSAGE)
        String newPassword,

        @NotBlank(message = "Please confirm your new password")
        String confirmPassword) {

    @AssertTrue(message = "Passwords do not match")
    public boolean isPasswordConfirmed() {
        return newPassword == null || confirmPassword == null || newPassword.equals(confirmPassword);
    }
}
