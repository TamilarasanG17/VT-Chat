package com.vtchat.service;

import com.vtchat.config.AppProperties;
import com.vtchat.entity.OtpPurpose;
import com.vtchat.entity.OtpToken;
import com.vtchat.exception.ApiException;
import com.vtchat.repository.OtpTokenRepository;
import com.vtchat.util.HashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional(noRollbackFor = ApiException.class)
public class OtpService {

    private static final Duration OPEN_FLOW_WINDOW = Duration.ofMinutes(30);
    private static final Duration RESET_GRANT_TTL = Duration.ofMinutes(10);

    private final OtpTokenRepository repo;
    private final AppProperties props;
    private final SecureRandom random = new SecureRandom();

    public String issue(String email, OtpPurpose purpose) {
        Instant now = Instant.now();
        long cooldown = props.getOtp().getResendCooldownSeconds();

        repo.findFirstByEmailAndPurposeOrderByCreatedAtDescIdDesc(email, purpose).ifPresent(last -> {
            long waitMillis = Duration.between(now, last.getCreatedAt().plusSeconds(cooldown)).toMillis();
            if (waitMillis > 0) {
                throw ApiException.tooManyRequests("OTP_COOLDOWN",
                        "Please wait before requesting another code", (int) Math.ceil(waitMillis / 1000.0));
            }
        });

        repo.invalidateActive(email, purpose);

        String otp = String.format("%04d", random.nextInt(10_000));
        repo.save(OtpToken.builder()
                .email(email)
                .purpose(purpose)
                .otpHash(hashOtp(email, purpose, otp))
                .expiresAt(now.plusSeconds(props.getOtp().getExpirationSeconds()))
                .attempts(0)
                .used(false)
                .build());
        return otp;
    }

    public void verify(String email, OtpPurpose purpose, String otp) {
        boolean verbose = purpose != OtpPurpose.RESET_PASSWORD;
        Instant now = Instant.now();
        int max = props.getOtp().getMaxAttempts();

        OtpToken token = repo.findFirstByEmailAndPurposeAndUsedFalseOrderByCreatedAtDescIdDesc(email, purpose).orElse(null);
        if (token == null || token.getExpiresAt().isBefore(now)) {
            throw verbose
                    ? ApiException.badRequest("OTP_EXPIRED", "This code has expired. Please request a new one.")
                    : genericInvalid();
        }
        if (token.getAttempts() >= max) {
            throw verbose ? tooManyAttempts() : genericInvalid();
        }

        if (!HashUtil.constantTimeEquals(token.getOtpHash(), hashOtp(email, purpose, otp))) {
            token.setAttempts(token.getAttempts() + 1);
            repo.save(token);
            if (!verbose) {
                throw genericInvalid();
            }
            int remaining = max - token.getAttempts();
            if (remaining <= 0) {
                throw tooManyAttempts();
            }
            throw ApiException.badRequest("INVALID_OTP",
                    "Incorrect code. " + remaining + (remaining == 1 ? " attempt" : " attempts") + " left.");
        }

        token.setUsed(true);
        repo.save(token);
    }

    @Transactional(readOnly = true, noRollbackFor = ApiException.class)
    public boolean hasOpenFlow(String email, OtpPurpose purpose) {
        return repo.findFirstByEmailAndPurposeOrderByCreatedAtDescIdDesc(email, purpose)
                .filter(t -> !t.isUsed())
                .filter(t -> t.getCreatedAt().isAfter(Instant.now().minus(OPEN_FLOW_WINDOW)))
                .isPresent();
    }

    public String issueResetGrant(String email) {
        repo.invalidateActive(email, OtpPurpose.RESET_GRANT);
        String token = HashUtil.randomUrlToken(32);
        repo.save(OtpToken.builder()
                .email(email)
                .purpose(OtpPurpose.RESET_GRANT)
                .otpHash(HashUtil.sha256Hex(token))
                .expiresAt(Instant.now().plus(RESET_GRANT_TTL))
                .attempts(0)
                .used(false)
                .build());
        return token;
    }

    public long resetGrantTtlSeconds() {
        return RESET_GRANT_TTL.toSeconds();
    }

    public void consumeResetGrant(String email, String token) {
        OtpToken grant = repo.findFirstByEmailAndPurposeAndUsedFalseOrderByCreatedAtDescIdDesc(email, OtpPurpose.RESET_GRANT)
                .orElseThrow(this::invalidResetToken);
        if (grant.getExpiresAt().isBefore(Instant.now()) || grant.getAttempts() >= props.getOtp().getMaxAttempts()) {
            throw invalidResetToken();
        }
        if (!HashUtil.constantTimeEquals(grant.getOtpHash(), HashUtil.sha256Hex(token))) {
            grant.setAttempts(grant.getAttempts() + 1);
            repo.save(grant);
            throw invalidResetToken();
        }
        grant.setUsed(true);
        repo.save(grant);
    }

    public void invalidateAll(String email) {
        repo.invalidateAllForEmail(email);
    }

    private String hashOtp(String email, OtpPurpose purpose, String otp) {
        return HashUtil.hmacSha256Hex(props.getOtp().getSecret(), email + ":" + purpose.name() + ":" + otp);
    }

    private ApiException genericInvalid() {
        return ApiException.badRequest("INVALID_OTP", "Invalid or expired code. Request a new code if needed.");
    }

    private ApiException tooManyAttempts() {
        return new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                "Too many incorrect attempts. Please request a new code.");
    }

    private ApiException invalidResetToken() {
        return ApiException.badRequest("INVALID_RESET_TOKEN",
                "This password reset session is invalid or has expired. Please start again.");
    }
}
