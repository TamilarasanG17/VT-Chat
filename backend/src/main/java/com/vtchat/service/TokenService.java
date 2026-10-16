package com.vtchat.service;

import com.vtchat.entity.RefreshToken;
import com.vtchat.entity.User;
import com.vtchat.exception.ApiException;
import com.vtchat.repository.RefreshTokenRepository;
import com.vtchat.security.JwtService;
import com.vtchat.util.HashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(noRollbackFor = ApiException.class)
public class TokenService {

    private static final long REUSE_GRACE_SECONDS = 10;

    private final RefreshTokenRepository repo;
    private final JwtService jwtService;

    public record TokenPair(String accessToken, long accessExpiresIn, String refreshToken, long refreshExpiresIn) {
    }

    public record Rotation(TokenPair tokens, User user) {
    }

    public TokenPair createSession(User user) {
        return issue(user, UUID.randomUUID().toString());
    }

    public Rotation rotate(String refreshToken) {
        jwtService.parseRefreshToken(refreshToken);

        RefreshToken stored = repo.findByTokenHash(HashUtil.sha256Hex(refreshToken))
                .orElseThrow(this::invalid);
        Instant now = Instant.now();

        if (stored.getRevokedAt() != null) {
            if (stored.getRevokedAt().plusSeconds(REUSE_GRACE_SECONDS).isBefore(now)) {
                repo.revokeSession(stored.getSessionId(), now);
            }
            throw invalid();
        }
        if (stored.getExpiresAt().isBefore(now)) {
            throw invalid();
        }

        User user = stored.getUser();
        if (!user.isEmailVerified()) {
            throw invalid();
        }

        stored.setRevokedAt(now);
        repo.save(stored);
        return new Rotation(issue(user, stored.getSessionId()), user);
    }

    public void revokeSession(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        repo.findByTokenHash(HashUtil.sha256Hex(refreshToken))
                .ifPresent(t -> repo.revokeSession(t.getSessionId(), Instant.now()));
    }

    public void revokeAllForUser(Long userId) {
        repo.revokeAllForUser(userId, Instant.now());
    }

    public void revokeOtherSessions(Long userId, String keepSessionId) {
        if (keepSessionId == null || keepSessionId.isBlank()) {
            repo.revokeAllForUser(userId, Instant.now());
        } else {
            repo.revokeOtherSessions(userId, keepSessionId, Instant.now());
        }
    }

    private TokenPair issue(User user, String sessionId) {
        String access = jwtService.generateAccessToken(user, sessionId);
        String refresh = jwtService.generateRefreshToken(user, sessionId);
        repo.save(RefreshToken.builder()
                .user(user)
                .sessionId(sessionId)
                .tokenHash(HashUtil.sha256Hex(refresh))
                .expiresAt(Instant.now().plusSeconds(jwtService.refreshTtlSeconds()))
                .build());
        return new TokenPair(access, jwtService.accessTtlSeconds(), refresh, jwtService.refreshTtlSeconds());
    }

    private ApiException invalid() {
        return ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Your session has expired. Please log in again.");
    }
}
