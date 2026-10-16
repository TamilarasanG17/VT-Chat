package com.vtchat.dto;

import com.vtchat.entity.OtpPurpose;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class AuthRequest {

    private AuthRequest() {
    }

    public static final String EMAIL_REGEX = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
    public static final String PASSWORD_REGEX =
            "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9])[\\x21-\\x7E]{8,64}$";
    public static final String PASSWORD_MESSAGE =
            "Password must be 8-64 characters with an uppercase letter, a lowercase letter, a number and a special character (no spaces)";

    public record Register(
            @NotBlank(message = "Username is required")
            @Size(min = 3, max = 30, message = "Username must be 3-30 characters")
            @Pattern(regexp = "^[A-Za-z0-9_.-]+$", message = "Username may only contain letters, numbers, dots, dashes and underscores")
            String username,

            @NotBlank(message = "Email is required")
            @Size(max = 254, message = "Email is too long")
            @Email(message = "Enter a valid email address")
            @Pattern(regexp = EMAIL_REGEX, message = "Enter a valid email address")
            String email,

            @NotBlank(message = "Password is required")
            @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE)
            String password) {
    }

    public record Login(
            @NotBlank(message = "Email is required")
            @Size(max = 254, message = "Email is too long")
            @Email(message = "Enter a valid email address")
            String email,

            @NotBlank(message = "Password is required")
            @Size(max = 128, message = "Password is too long")
            String password) {
    }

    public record VerifyOtp(
            @NotBlank(message = "Email is required")
            @Size(max = 254, message = "Email is too long")
            @Email(message = "Enter a valid email address")
            String email,

            @NotBlank(message = "OTP is required")
            @Pattern(regexp = "^\\d{4}$", message = "OTP must be exactly 4 digits")
            String otp) {
    }

    public record ResendOtp(
            @NotBlank(message = "Email is required")
            @Size(max = 254, message = "Email is too long")
            @Email(message = "Enter a valid email address")
            String email,

            @NotNull(message = "Purpose is required")
            OtpPurpose purpose) {

        @AssertTrue(message = "Invalid purpose")
        public boolean isPurposeResendable() {
            return purpose == null || purpose.isUserFacing();
        }
    }

    public record ForgotPassword(
            @NotBlank(message = "Email is required")
            @Size(max = 254, message = "Email is too long")
            @Email(message = "Enter a valid email address")
            String email) {
    }

    public record ResetPassword(
            @NotBlank(message = "Email is required")
            @Size(max = 254, message = "Email is too long")
            @Email(message = "Enter a valid email address")
            String email,

            @NotBlank(message = "Reset token is required")
            @Size(max = 200, message = "Invalid reset token")
            String resetToken,

            @NotBlank(message = "New password is required")
            @Pattern(regexp = PASSWORD_REGEX, message = PASSWORD_MESSAGE)
            String newPassword,

            @NotBlank(message = "Please confirm your new password")
            String confirmPassword) {

        @AssertTrue(message = "Passwords do not match")
        public boolean isPasswordConfirmed() {
            return newPassword == null || confirmPassword == null || newPassword.equals(confirmPassword);
        }
    }
}
