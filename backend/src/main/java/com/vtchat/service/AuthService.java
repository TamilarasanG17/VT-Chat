package com.vtchat.service;

import com.vtchat.config.AppProperties;
import com.vtchat.dto.AuthRequest;
import com.vtchat.dto.AuthResponses.OtpSent;
import com.vtchat.dto.AuthResponses.ResetToken;
import com.vtchat.dto.user.UserResponse;
import com.vtchat.entity.OtpPurpose;
import com.vtchat.entity.User;
import com.vtchat.exception.ApiException;
import com.vtchat.repository.UserRepository;
import com.vtchat.util.EmailUtil;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(noRollbackFor = ApiException.class)
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final OtpService otpService;
    private final EmailService emailService;
    private final TokenService tokenService;
    private final AppProperties props;
    private final UserCache userCache;

    private String dummyHash;

    public record AuthResult(TokenService.TokenPair tokens, UserResponse user) {
    }

    @PostConstruct
    void init() {
        dummyHash = passwordEncoder.encode("vtchat-timing-equalizer");
    }

    public OtpSent register(AuthRequest.Register req) {
        String email = EmailUtil.normalize(req.email());
        String hash = passwordEncoder.encode(req.password());

        Optional<User> existing = users.findByEmail(email);
        if (existing.isPresent()) {
            User user = existing.get();
            if (user.isEmailVerified()) {
                throw ApiException.conflict("EMAIL_ALREADY_EXISTS", "An account with this email already exists");
            }
            user.setUsername(req.username().trim());
            user.setPasswordHash(hash);
            users.save(user);
        } else {
            users.save(User.builder()
                    .username(req.username().trim())
                    .email(email)
                    .passwordHash(hash)
                    .emailVerified(false)
                    .build());
        }

        sendOtp(email, OtpPurpose.REGISTER);
        return otpSent(email, OtpPurpose.REGISTER, "VERIFY_REGISTER_OTP");
    }

    public AuthResult verifyRegisterOtp(AuthRequest.VerifyOtp req) {
        String email = EmailUtil.normalize(req.email());
        otpService.verify(email, OtpPurpose.REGISTER, req.otp());

        User user = users.findByEmail(email)
                .orElseThrow(() -> ApiException.badRequest("INVALID_OTP", "Invalid or expired code"));
        user.setEmailVerified(true);
        users.save(user);
        return startSession(user);
    }

    public OtpSent login(AuthRequest.Login req) {
        String email = EmailUtil.normalize(req.email());
        Optional<User> found = users.findByEmail(email);

        boolean passwordOk = passwordEncoder.matches(req.password(),
                found.map(User::getPasswordHash).orElse(dummyHash));
        if (found.isEmpty() || !passwordOk) {
            throw ApiException.unauthorized("INVALID_CREDENTIALS", "Invalid email or password");
        }

        User user = found.get();
        if (!user.isEmailVerified()) {
            sendOtp(email, OtpPurpose.REGISTER);
            return otpSent(email, OtpPurpose.REGISTER, "VERIFY_REGISTER_OTP");
        }
        sendOtp(email, OtpPurpose.LOGIN);
        return otpSent(email, OtpPurpose.LOGIN, "VERIFY_LOGIN_OTP");
    }

    public AuthResult verifyLoginOtp(AuthRequest.VerifyOtp req) {
        String email = EmailUtil.normalize(req.email());
        otpService.verify(email, OtpPurpose.LOGIN, req.otp());

        User user = users.findByEmail(email)
                .filter(User::isEmailVerified)
                .orElseThrow(() -> ApiException.badRequest("INVALID_OTP", "Invalid or expired code"));
        return startSession(user);
    }

    public OtpSent resendOtp(AuthRequest.ResendOtp req) {
        String email = EmailUtil.normalize(req.email());
        OtpPurpose purpose = req.purpose();
        Optional<User> user = users.findByEmail(email);

        boolean eligible = user.isPresent()
                && canResend(user.get(), purpose)
                && otpService.hasOpenFlow(email, purpose);

        if (eligible) {
            try {
                if (purpose == OtpPurpose.RESET_PASSWORD) {
                    emailService.sendOtpAsync(email, otpService.issue(email, purpose), purpose);
                } else {
                    sendOtp(email, purpose);
                }
            } catch (ApiException e) {
                boolean cooldown = "OTP_COOLDOWN".equals(e.getCode());
                if (!(cooldown && purpose != OtpPurpose.REGISTER)) {
                    throw e;
                }
            }
        }
        return otpSent(email, purpose, nextStepFor(purpose));
    }

    public OtpSent forgotPassword(AuthRequest.ForgotPassword req) {
        String email = EmailUtil.normalize(req.email());
        users.findByEmail(email).filter(User::isEmailVerified).ifPresent(user -> {
            try {
                String otp = otpService.issue(email, OtpPurpose.RESET_PASSWORD);
                emailService.sendOtpAsync(email, otp, OtpPurpose.RESET_PASSWORD);
            } catch (ApiException cooldown) {
            }
        });
        return otpSent(email, OtpPurpose.RESET_PASSWORD, "VERIFY_RESET_OTP");
    }

    public ResetToken verifyResetOtp(AuthRequest.VerifyOtp req) {
        String email = EmailUtil.normalize(req.email());
        otpService.verify(email, OtpPurpose.RESET_PASSWORD, req.otp());
        return new ResetToken(otpService.issueResetGrant(email), otpService.resetGrantTtlSeconds());
    }

    public void resetPassword(AuthRequest.ResetPassword req) {
        String email = EmailUtil.normalize(req.email());
        otpService.consumeResetGrant(email, req.resetToken());

        User user = users.findByEmail(email)
                .orElseThrow(() -> ApiException.badRequest("INVALID_RESET_TOKEN",
                        "This password reset session is invalid or has expired. Please start again."));
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        users.save(user);

        tokenService.revokeAllForUser(user.getId());
        otpService.invalidateAll(email);
    }

    public AuthResult refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Your session has expired. Please log in again.");
        }
        TokenService.Rotation rotation = tokenService.rotate(refreshToken);
        return new AuthResult(rotation.tokens(), toInfo(rotation.user()));
    }

    public void logout(String refreshToken) {
        tokenService.revokeSession(refreshToken);
    }

    @Transactional(readOnly = true, noRollbackFor = ApiException.class)
    public UserResponse me(Long userId) {
        return userCache.profile(userId, () -> users.findById(userId).filter(User::isEmailVerified).map(this::toInfo))
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHORIZED", "Authentication required"));
    }

    private AuthResult startSession(User user) {
        return new AuthResult(tokenService.createSession(user), toInfo(user));
    }

    private void sendOtp(String email, OtpPurpose purpose) {
        String otp = otpService.issue(email, purpose);
        emailService.sendOtp(email, otp, purpose);
    }

    private OtpSent otpSent(String email, OtpPurpose purpose, String nextStep) {
        return new OtpSent(email, purpose.name(), nextStep,
                props.getOtp().getExpirationSeconds(), props.getOtp().getResendCooldownSeconds());
    }

    private boolean canResend(User user, OtpPurpose purpose) {
        return switch (purpose) {
            case REGISTER -> !user.isEmailVerified();
            case LOGIN, RESET_PASSWORD -> user.isEmailVerified();
            default -> false;
        };
    }

    private String nextStepFor(OtpPurpose purpose) {
        return switch (purpose) {
            case REGISTER -> "VERIFY_REGISTER_OTP";
            case LOGIN -> "VERIFY_LOGIN_OTP";
            default -> "VERIFY_RESET_OTP";
        };
    }

    private UserResponse toInfo(User user) {
        return UserResponse.from(user);
    }
}
