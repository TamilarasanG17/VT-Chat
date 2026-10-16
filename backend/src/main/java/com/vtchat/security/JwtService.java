package com.vtchat.security;

import com.vtchat.config.AppProperties;
import com.vtchat.entity.User;
import com.vtchat.exception.ApiException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class JwtService {

    private static final String ISSUER = "vtchat";
    private static final String TYPE_CLAIM = "typ";
    private static final String TYPE_ACCESS = "access";
    private static final String TYPE_REFRESH = "refresh";
    private static final String TYPE_MEDIA = "media";
    private static final String TYPE_KNOWLEDGE = "knowledge";
    public static final long MEDIA_TOKEN_TTL_SECONDS = 3600;

    private final AppProperties props;

    private SecretKey accessKey;
    private SecretKey refreshKey;

    @PostConstruct
    void init() {
        String access = props.getJwt().getSecret();
        String refresh = props.getJwt().getRefreshSecret();
        if (access == null || access.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be set and at least 32 characters (try: openssl rand -base64 48)");
        }
        if (refresh == null || refresh.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_REFRESH_SECRET must be set and at least 32 characters (try: openssl rand -base64 48)");
        }
        if (access.equals(refresh)) {
            throw new IllegalStateException("JWT_SECRET and JWT_REFRESH_SECRET must be different");
        }
        String otp = props.getOtp().getSecret();
        if (otp == null || otp.length() < 16) {
            throw new IllegalStateException("OTP_SECRET must be set and at least 16 characters (try: openssl rand -base64 32)");
        }
        this.accessKey = Keys.hmacShaKeyFor(access.getBytes(StandardCharsets.UTF_8));
        this.refreshKey = Keys.hmacShaKeyFor(refresh.getBytes(StandardCharsets.UTF_8));
    }

    public long accessTtlSeconds() {
        return props.getJwt().getAccessExpiration() / 1000;
    }

    public long refreshTtlSeconds() {
        return props.getJwt().getRefreshExpiration() / 1000;
    }

    public String generateAccessToken(User user, String sessionId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getId()))
                .claim("email", user.getEmail())
                .claim("username", user.getUsername())
                .claim("sid", sessionId)
                .claim(TYPE_CLAIM, TYPE_ACCESS)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(props.getJwt().getAccessExpiration())))
                .signWith(accessKey)
                .compact();
    }

    public String generateRefreshToken(User user, String sessionId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(user.getId()))
                .id(UUID.randomUUID().toString())
                .claim("sid", sessionId)
                .claim(TYPE_CLAIM, TYPE_REFRESH)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusMillis(props.getJwt().getRefreshExpiration())))
                .signWith(refreshKey)
                .compact();
    }

    public record ParsedAccessToken(AuthenticatedUser user, Instant expiresAt) {
    }

    public AuthenticatedUser parseAccessToken(String token) {
        return parseAccessTokenWithExpiry(token).user();
    }

    public ParsedAccessToken parseAccessTokenWithExpiry(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(accessKey)
                .requireIssuer(ISSUER)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        if (!TYPE_ACCESS.equals(claims.get(TYPE_CLAIM, String.class))) {
            throw new JwtException("Not an access token");
        }
        AuthenticatedUser user = new AuthenticatedUser(
                Long.valueOf(claims.getSubject()),
                claims.get("email", String.class),
                claims.get("username", String.class),
                claims.get("sid", String.class));
        return new ParsedAccessToken(user, claims.getExpiration().toInstant());
    }

    public String generateMediaToken(Long userId, Long messageId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(userId))
                .claim("mid", messageId)
                .claim(TYPE_CLAIM, TYPE_MEDIA)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(MEDIA_TOKEN_TTL_SECONDS)))
                .signWith(accessKey)
                .compact();
    }

    public AuthenticatedUser parseMediaToken(String token, Long messageId) {
        Claims claims = Jwts.parser()
                .verifyWith(accessKey)
                .requireIssuer(ISSUER)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Number tokenMessageId = claims.get("mid", Number.class);
        if (!TYPE_MEDIA.equals(claims.get(TYPE_CLAIM, String.class))
                || tokenMessageId == null || tokenMessageId.longValue() != messageId) {
            throw new JwtException("Not a media token for this message");
        }
        return new AuthenticatedUser(Long.valueOf(claims.getSubject()), null, null, null);
    }

    public String generateKnowledgeToken(Long userId, Long documentId, long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .issuer(ISSUER)
                .subject(String.valueOf(userId))
                .claim("kid", documentId)
                .claim(TYPE_CLAIM, TYPE_KNOWLEDGE)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(accessKey)
                .compact();
    }

    public AuthenticatedUser parseKnowledgeToken(String token, Long documentId) {
        Claims claims = Jwts.parser()
                .verifyWith(accessKey)
                .requireIssuer(ISSUER)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        Number tokenDocumentId = claims.get("kid", Number.class);
        if (!TYPE_KNOWLEDGE.equals(claims.get(TYPE_CLAIM, String.class))
                || tokenDocumentId == null || tokenDocumentId.longValue() != documentId) {
            throw new JwtException("Not a knowledge token for this document");
        }
        return new AuthenticatedUser(Long.valueOf(claims.getSubject()), null, null, null);
    }

    public Claims parseRefreshToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(refreshKey)
                    .requireIssuer(ISSUER)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            if (!TYPE_REFRESH.equals(claims.get(TYPE_CLAIM, String.class))) {
                throw new JwtException("Not a refresh token");
            }
            return claims;
        } catch (JwtException | IllegalArgumentException e) {
            throw ApiException.unauthorized("INVALID_REFRESH_TOKEN", "Your session has expired. Please log in again.");
        }
    }
}
