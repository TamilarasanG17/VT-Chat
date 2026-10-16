package com.vtchat.ratelimit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtchat.config.RateLimitProperties;
import com.vtchat.dto.ErrorResponse;
import com.vtchat.security.AuthenticatedUser;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Pattern TRANSLATE = Pattern.compile("^/api/messages/\\d+/translate$");
    private static final Pattern MEDIA_UPLOAD = Pattern.compile("^/api/conversations/\\d+/(media|voice)$");
    private static final Pattern AI_SEND = Pattern.compile("^/api/ai/conversations/\\d+/messages(/stream)?$");
    private static final Pattern RAG_SEND = Pattern.compile("^/api/ai/conversations/\\d+/rag(/stream)?$");
    private static final Pattern RAG_REPROCESS = Pattern.compile("^/api/ai/knowledge/documents/\\d+/reprocess$");
    private static final Pattern FORWARD = Pattern.compile("^/api/messages/\\d+/forward$");
    private static final Pattern MESSAGE_ACTION = Pattern.compile("^/api/messages/\\d+(/reactions|/for-me|/for-everyone)?$");

    private final RateLimitService limiter;
    private final RateLimitProperties props;
    private final ObjectMapper mapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !limiter.isEnabled() || HttpMethod.OPTIONS.matches(request.getMethod()) || !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        String method = request.getMethod();
        RateLimitBucket bucket = bucketFor(method, path);
        if (bucket != null) {
            RateLimitService.Decision decision = limiter.check(bucket, subjectFor(bucket, request, path));
            if (!decision.allowed()) {
                reject(response, decision.retryAfterSeconds());
                return;
            }
        }
        chain.doFilter(request, response);
    }

    static RateLimitBucket bucketFor(String method, String path) {
        boolean post = HttpMethod.POST.matches(method);
        if (post && path.startsWith("/api/auth/")) {
            return switch (path) {
                case "/api/auth/login", "/api/auth/register", "/api/auth/forgot-password", "/api/auth/reset-password" -> RateLimitBucket.AUTH;
                case "/api/auth/verify-register-otp", "/api/auth/verify-login-otp", "/api/auth/verify-reset-otp",
                     "/api/auth/resend-otp" -> RateLimitBucket.OTP;
                default -> null;
            };
        }
        if (HttpMethod.GET.matches(method) && path.equals("/api/users/search")) {
            return RateLimitBucket.SEARCH;
        }
        if (post && (path.equals("/api/ai/chat") || AI_SEND.matcher(path).matches())) {
            return RateLimitBucket.AI;
        }
        if (post && path.equals("/api/ai/knowledge/documents")) {
            return RateLimitBucket.RAG_UPLOAD;
        }
        if (post && RAG_REPROCESS.matcher(path).matches()) {
            return RateLimitBucket.RAG_REPROCESS;
        }
        if (post && RAG_SEND.matcher(path).matches()) {
            return RateLimitBucket.RAG_QUERY;
        }
        if (post && TRANSLATE.matcher(path).matches()) {
            return RateLimitBucket.TRANSLATION;
        }
        if (post && MEDIA_UPLOAD.matcher(path).matches()) {
            return RateLimitBucket.MEDIA;
        }
        if (post && FORWARD.matcher(path).matches()) {
            return RateLimitBucket.MESSAGE;
        }
        if (path.startsWith("/api/notifications")) {
            return RateLimitBucket.NOTIFICATION;
        }
        if (!HttpMethod.GET.matches(method) && MESSAGE_ACTION.matcher(path).matches()) {
            return RateLimitBucket.CHAT_ACTION;
        }
        return null;
    }

    private String subjectFor(RateLimitBucket bucket, HttpServletRequest request, String path) {
        if (bucket == RateLimitBucket.AUTH || bucket == RateLimitBucket.OTP) {
            return "ip:" + clientIp(request) + ":" + path.substring("/api/auth/".length());
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof AuthenticatedUser user) {
            return "user:" + user.id();
        }
        return "ip:" + clientIp(request);
    }

    private String clientIp(HttpServletRequest request) {
        if (props.isTrustForwardedHeaders()) {
            String forwarded = request.getHeader("X-Forwarded-For");
            if (forwarded != null && !forwarded.isBlank()) {
                String[] parts = forwarded.split(",");
                String last = parts[parts.length - 1].trim();
                if (!last.isEmpty() && last.length() <= 45) {
                    return last;
                }
            }
        }
        return request.getRemoteAddr();
    }

    private void reject(HttpServletResponse response, int retryAfter) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        mapper.writeValue(response.getWriter(),
                ErrorResponse.of("Too many requests. Please try again later.", "RATE_LIMITED", retryAfter, null));
    }
}
