package com.vtchat.service;

import com.vtchat.config.AppProperties;
import com.vtchat.dto.presence.PresenceEvent;
import com.vtchat.dto.presence.PresenceResponse;
import com.vtchat.dto.user.UserResponse;
import com.vtchat.entity.User;
import com.vtchat.entity.UserPresence;
import com.vtchat.exception.ApiException;
import com.vtchat.repository.ConversationRepository;
import com.vtchat.repository.UserPresenceRepository;
import com.vtchat.repository.UserRepository;
import com.vtchat.security.StompUser;
import com.vtchat.websocket.PresenceEventPublisher;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class PresenceService {

    private final PresenceRegistry registry;
    private final UserPresenceRepository presenceRepo;
    private final UserRepository users;
    private final ConversationRepository conversations;
    private final PresenceEventPublisher publisher;
    private final TypingService typing;
    private final DeliveryService delivery;
    private final long graceSeconds;
    private final UserCache userCache;

    private final Object lock = new Object();
    private final Map<Long, ScheduledFuture<?>> pendingOffline = new HashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "presence-offline-grace");
        t.setDaemon(true);
        return t;
    });

    public PresenceService(PresenceRegistry registry, UserPresenceRepository presenceRepo, UserRepository users,
                           ConversationRepository conversations, PresenceEventPublisher publisher,
                           TypingService typing, DeliveryService delivery, AppProperties props, UserCache userCache) {
        this.userCache = userCache;
        this.registry = registry;
        this.presenceRepo = presenceRepo;
        this.users = users;
        this.conversations = conversations;
        this.publisher = publisher;
        this.typing = typing;
        this.delivery = delivery;
        this.graceSeconds = Math.max(0, props.getPresence().getOfflineGraceSeconds());
    }

    @EventListener
    public void onConnected(SessionConnectedEvent event) {
        Long userId = userId(event.getUser());
        String sessionId = SimpMessageHeaderAccessor.getSessionId(event.getMessage().getHeaders());
        if (userId == null || sessionId == null) {
            return;
        }
        synchronized (lock) {
            if (!registry.connect(userId, sessionId)) {
                return;
            }
            safely(() -> delivery.deliverPendingTo(userId));
            ScheduledFuture<?> pending = pendingOffline.remove(userId);
            if (pending != null) {
                pending.cancel(false);
                return;
            }
            safely(() -> publishState(userId, null));
        }
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        Long userId = userId(event.getUser());
        if (userId == null) {
            return;
        }
        synchronized (lock) {
            if (!registry.disconnect(userId, event.getSessionId())) {
                return;
            }
            ScheduledFuture<?> old = pendingOffline.remove(userId);
            if (old != null) {
                old.cancel(false);
            }
            pendingOffline.put(userId, scheduler.schedule(() -> goOffline(userId), graceSeconds, TimeUnit.SECONDS));
        }
    }

    private void goOffline(Long userId) {
        synchronized (lock) {
            pendingOffline.remove(userId);
            if (registry.isConnected(userId)) {
                return;
            }
            Instant now = Instant.now();
            safely(() -> saveLastSeen(userId, now));
            safely(() -> typing.clearFor(userId));
            safely(() -> publishState(userId, now));
        }
    }

    private boolean isOnline(Long userId) {
        synchronized (lock) {
            return registry.isConnected(userId) || pendingOffline.containsKey(userId);
        }
    }

    private void saveLastSeen(Long userId, Instant at) {
        if (presenceRepo.touch(userId, at) > 0) {
            return;
        }
        try {
            presenceRepo.saveAndFlush(new UserPresence(userId, at, at));
        } catch (DataIntegrityViolationException e) {
            presenceRepo.touch(userId, at);
        }
    }

    public PresenceResponse getPresence(Long viewerId, Long targetId) {
        User target = users.findById(targetId).orElseThrow(PresenceService::notFound);
        if (!viewerId.equals(targetId) && !sharesConversation(viewerId, targetId)) {
            throw notFound();
        }
        if (viewerId.equals(targetId)) {
            return new PresenceResponse(targetId, isOnline(targetId) ? PresenceResponse.ONLINE : PresenceResponse.OFFLINE, null);
        }
        return view(target, storedLastSeen(targetId));
    }

    public List<PresenceResponse> getPeersPresence(Long viewerId) {
        List<Long> peerIds = conversations.findPeerIds(viewerId);
        if (peerIds.isEmpty()) {
            return List.of();
        }
        Map<Long, Instant> seen = new HashMap<>();
        presenceRepo.findAllById(peerIds).forEach(p -> seen.put(p.getUserId(), p.getLastSeenAt()));
        List<PresenceResponse> out = new ArrayList<>();
        for (User peer : users.findAllById(peerIds)) {
            out.add(view(peer, seen.get(peer.getId())));
        }
        return out;
    }

    private PresenceResponse view(User user, Instant storedLastSeen) {
        Instant lastSeen = user.isShowLastSeen() ? storedLastSeen : null;
        if (!user.isShowOnlineStatus()) {
            return new PresenceResponse(user.getId(), PresenceResponse.HIDDEN, lastSeen);
        }
        return isOnline(user.getId())
                ? new PresenceResponse(user.getId(), PresenceResponse.ONLINE, null)
                : new PresenceResponse(user.getId(), PresenceResponse.OFFLINE, lastSeen);
    }

    public UserResponse updatePrivacy(Long userId, boolean showOnlineStatus, boolean showLastSeen) {
        User user = users.findById(userId).orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "User not found"));
        user.setShowOnlineStatus(showOnlineStatus);
        user.setShowLastSeen(showLastSeen);
        users.save(user);
        userCache.invalidate(userId);
        safely(() -> {
            PresenceResponse now = view(user, storedLastSeen(userId));
            sendToConnectedPeers(userId, PresenceEvent.of(now));
        });
        return UserResponse.from(user);
    }

    private void publishState(Long userId, Instant knownLastSeen) {
        User user = users.findById(userId).orElse(null);
        if (user == null || !user.isShowOnlineStatus()) {
            return;
        }
        sendToConnectedPeers(userId, PresenceEvent.of(view(user, knownLastSeen != null ? knownLastSeen : storedLastSeen(userId))));
    }

    private void sendToConnectedPeers(Long userId, PresenceEvent event) {
        for (Long peerId : conversations.findPeerIds(userId)) {
            if (registry.isConnected(peerId)) {
                publisher.presence(peerId, event);
            }
        }
    }

    private Instant storedLastSeen(Long userId) {
        return presenceRepo.findById(userId).map(UserPresence::getLastSeenAt).orElse(null);
    }

    private boolean sharesConversation(Long a, Long b) {
        Long low = Math.min(a, b);
        Long high = Math.max(a, b);
        return conversations.findByUserOneIdAndUserTwoId(low, high).isPresent();
    }

    private static ApiException notFound() {
        return ApiException.notFound("USER_NOT_FOUND", "User not found");
    }

    private static Long userId(Principal principal) {
        return principal instanceof StompUser user ? user.id() : null;
    }

    private void safely(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("Presence update failed: {}", e.getClass().getSimpleName());
        }
    }

    @PreDestroy
    void shutdown() {
        scheduler.shutdownNow();
    }
}
