package com.vtchat.service;

import com.vtchat.config.AppProperties;
import com.vtchat.security.StompUser;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Component
@RequiredArgsConstructor
public class SignalingSessions {

    private final AppProperties props;
    private final CallService callService;
    private final PresenceRegistry presence;

    private final Map<Long, AtomicInteger> open = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "call-disconnect-grace");
        t.setDaemon(true);
        return t;
    });

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        Long userId = userId(event.getUser());
        if (userId != null) {
            open.computeIfAbsent(userId, id -> new AtomicInteger()).incrementAndGet();
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        Long userId = userId(event.getUser());
        if (userId == null) {
            return;
        }
        AtomicInteger count = open.get(userId);
        if (count == null || count.decrementAndGet() > 0) {
            return;
        }
        scheduler.schedule(() -> {
            try {
                AtomicInteger now = open.get(userId);
                if ((now == null || now.get() <= 0) && !presence.isConnected(userId)) {
                    callService.endCallsOfDisconnectedUser(userId);
                }
            } catch (RuntimeException e) {
                log.warn("Cleanup after disconnect failed: {}", e.getClass().getSimpleName());
            }
        }, props.getCall().getDisconnectGraceSeconds(), TimeUnit.SECONDS);
    }

    private static Long userId(Principal principal) {
        return principal instanceof StompUser user ? user.id() : null;
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}
