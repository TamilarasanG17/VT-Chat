package com.vtchat.security;

import com.vtchat.dto.message.WsErrorResponse;
import com.vtchat.exception.ApiException;
import com.vtchat.ratelimit.RateLimitBucket;
import com.vtchat.ratelimit.RateLimitService;
import com.vtchat.service.MessageService;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class StompAuthChannelInterceptor implements ChannelInterceptor {

    private static final Set<String> SEND_DESTINATIONS = Set.of(
            "/app/chat.send", "/app/chat.read", "/app/chat.active", "/app/message.react", "/app/message.unreact", "/app/message.edit", "/app/message.delete",
            "/app/call.offer", "/app/call.answer", "/app/call.ice", "/app/call.reject", "/app/call.cancel",
            "/app/call.end", "/app/call.connected", "/app/chat.typing");
    private static final Set<String> USER_QUEUES = Set.of("/user/queue/errors", "/user/queue/messages", "/user/queue/call",
            "/user/queue/presence", "/user/queue/typing", "/user/queue/notifications");
    private static final Pattern CHAT_TOPIC = Pattern.compile("^/topic/chat/(\\d{1,18})$");
    private static final String EXPIRY_ATTRIBUTE = "vtchat.token_expiry";

    private final JwtService jwtService;
    private final MessageService messageService;
    private final RateLimitService rateLimiter;
    private final SimpMessagingTemplate template;

    public StompAuthChannelInterceptor(JwtService jwtService, MessageService messageService, RateLimitService rateLimiter,
                                       @Lazy SimpMessagingTemplate template) {
        this.jwtService = jwtService;
        this.messageService = messageService;
        this.rateLimiter = rateLimiter;
        this.template = template;
    }

    private static RateLimitBucket bucketFor(String destination) {
        return switch (destination) {
            case "/app/chat.send" -> RateLimitBucket.MESSAGE;
            case "/app/call.ice" -> RateLimitBucket.CALL_ICE;
            case "/app/call.offer", "/app/call.answer", "/app/call.reject", "/app/call.cancel", "/app/call.end",
                 "/app/call.connected" -> RateLimitBucket.CALL_SIGNAL;
            case "/app/chat.typing" -> RateLimitBucket.TYPING;
            default -> RateLimitBucket.CHAT_ACTION;
        };
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        StompCommand command = accessor == null ? null : accessor.getCommand();
        if (command == null) {
            return message;
        }

        switch (command) {
            case CONNECT, STOMP -> authenticate(accessor);
            case SEND -> {
                StompUser sender = requireUser(accessor);
                String target = accessor.getDestination();
                if (target == null || !SEND_DESTINATIONS.contains(target)) {
                    throw new WebSocketAuthException("Destination not allowed");
                }
                RateLimitService.Decision decision = rateLimiter.check(bucketFor(target), "user:" + sender.id());
                if (!decision.allowed()) {
                    template.convertAndSendToUser(String.valueOf(sender.id()), "/queue/errors",
                            new WsErrorResponse(429, "RATE_LIMITED", "Too many requests. Please slow down.", decision.retryAfterSeconds()));
                    return null;
                }
            }
            case SUBSCRIBE -> authorizeSubscription(accessor, requireUser(accessor));
            case UNSUBSCRIBE, DISCONNECT -> {
            }
            default -> throw new WebSocketAuthException("Command not allowed");
        }
        return message;
    }

    private void authenticate(StompHeaderAccessor accessor) {
        String header = accessor.getFirstNativeHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            throw new WebSocketAuthException("Authentication required");
        }
        JwtService.ParsedAccessToken parsed;
        try {
            parsed = jwtService.parseAccessTokenWithExpiry(header.substring(7).trim());
        } catch (JwtException | IllegalArgumentException e) {
            throw new WebSocketAuthException("Authentication required");
        }
        accessor.setUser(new StompUser(parsed.user().id(), parsed.user().username()));
        Map<String, Object> session = accessor.getSessionAttributes();
        if (session != null) {
            session.put(EXPIRY_ATTRIBUTE, parsed.expiresAt());
        }
    }

    private StompUser requireUser(StompHeaderAccessor accessor) {
        if (!(accessor.getUser() instanceof StompUser user)) {
            throw new WebSocketAuthException("Authentication required");
        }
        Map<String, Object> session = accessor.getSessionAttributes();
        Object expiry = session == null ? null : session.get(EXPIRY_ATTRIBUTE);
        if (expiry instanceof Instant expiresAt && Instant.now().isAfter(expiresAt)) {
            throw new WebSocketAuthException("Session expired");
        }
        return user;
    }

    private void authorizeSubscription(StompHeaderAccessor accessor, StompUser user) {
        String destination = accessor.getDestination();
        if (destination != null && USER_QUEUES.contains(destination)) {
            return;
        }
        Matcher matcher = CHAT_TOPIC.matcher(destination == null ? "" : destination);
        if (!matcher.matches()) {
            throw new WebSocketAuthException("Subscription not allowed");
        }
        try {
            messageService.assertParticipant(Long.parseLong(matcher.group(1)), user.id());
        } catch (ApiException e) {
            throw new WebSocketAuthException("You do not have access to this conversation");
        }
    }
}
