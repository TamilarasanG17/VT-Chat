package com.vtchat.controller;

import com.vtchat.config.AppProperties;
import com.vtchat.dto.ApiResponse;
import com.vtchat.dto.AuthRequest;
import com.vtchat.dto.AuthResponses.OtpSent;
import com.vtchat.dto.AuthResponses.ResetToken;
import com.vtchat.dto.AuthResponses.Session;
import com.vtchat.dto.user.UserResponse;
import com.vtchat.exception.ApiException;
import com.vtchat.security.AuthenticatedUser;
import com.vtchat.service.AuthService;
import com.vtchat.service.AuthService.AuthResult;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private static final String COOKIE_PATH = "/api/auth";

    private final AuthService authService;
    private final AppProperties props;

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<OtpSent>> register(@Valid @RequestBody AuthRequest.Register req) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Verification code sent to your email", authService.register(req)));
    }

    @PostMapping("/verify-register-otp")
    public ResponseEntity<ApiResponse<Session>> verifyRegisterOtp(@Valid @RequestBody AuthRequest.VerifyOtp req) {
        return sessionResponse(authService.verifyRegisterOtp(req), "Email verified successfully");
    }

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<OtpSent>> login(@Valid @RequestBody AuthRequest.Login req) {
        return ResponseEntity.ok(ApiResponse.ok("Verification code sent to your email", authService.login(req)));
    }

    @PostMapping("/verify-login-otp")
    public ResponseEntity<ApiResponse<Session>> verifyLoginOtp(@Valid @RequestBody AuthRequest.VerifyOtp req) {
        return sessionResponse(authService.verifyLoginOtp(req), "Logged in successfully");
    }

    @PostMapping("/resend-otp")
    public ResponseEntity<ApiResponse<OtpSent>> resendOtp(@Valid @RequestBody AuthRequest.ResendOtp req) {
        return ResponseEntity.ok(ApiResponse.ok("If a verification is pending, a new code has been sent", authService.resendOtp(req)));
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<ApiResponse<OtpSent>> forgotPassword(@Valid @RequestBody AuthRequest.ForgotPassword req) {
        return ResponseEntity.ok(ApiResponse.ok(
                "If an account exists for this email, a verification code has been sent", authService.forgotPassword(req)));
    }

    @PostMapping("/verify-reset-otp")
    public ResponseEntity<ApiResponse<ResetToken>> verifyResetOtp(@Valid @RequestBody AuthRequest.VerifyOtp req) {
        return ResponseEntity.ok(ApiResponse.ok("OTP verified successfully", authService.verifyResetOtp(req)));
    }

    @PostMapping("/reset-password")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> resetPassword(
            @Valid @RequestBody AuthRequest.ResetPassword req) {
        authService.resetPassword(req);
        return ResponseEntity.ok(ApiResponse.ok("Password reset successfully. Please log in."));
    }

    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<Session>> refresh(HttpServletRequest request, HttpServletResponse response) {
        try {
            return sessionResponse(authService.refresh(readRefreshCookie(request)), "Token refreshed");
        } catch (ApiException e) {
            response.addHeader(HttpHeaders.SET_COOKIE, clearCookie().toString());
            throw e;
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> logout(HttpServletRequest request) {
        authService.logout(readRefreshCookie(request));
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, clearCookie().toString())
                .body(ApiResponse.ok("Logged out successfully"));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserResponse>> me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok("OK", authService.me(principal.id())));
    }

    private ResponseEntity<ApiResponse<Session>> sessionResponse(AuthResult result, String message) {
        Session session = new Session(result.tokens().accessToken(), "Bearer",
                result.tokens().accessExpiresIn(), result.user());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie(result.tokens().refreshToken(),
                        result.tokens().refreshExpiresIn()).toString())
                .body(ApiResponse.ok(message, session));
    }

    private ResponseCookie refreshCookie(String token, long maxAgeSeconds) {
        return ResponseCookie.from(props.getCookie().getName(), token)
                .httpOnly(true)
                .secure(props.getCookie().isSecure())
                .sameSite(props.getCookie().getSameSite())
                .path(COOKIE_PATH)
                .maxAge(Duration.ofSeconds(maxAgeSeconds))
                .build();
    }

    private ResponseCookie clearCookie() {
        return ResponseCookie.from(props.getCookie().getName(), "")
                .httpOnly(true)
                .secure(props.getCookie().isSecure())
                .sameSite(props.getCookie().getSameSite())
                .path(COOKIE_PATH)
                .maxAge(Duration.ZERO)
                .build();
    }

    private String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (props.getCookie().getName().equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
