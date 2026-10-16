package com.vtchat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtchat.config.AppProperties;
import com.vtchat.dto.call.CallAction;
import com.vtchat.dto.call.CallAnswer;
import com.vtchat.dto.call.CallEvent;
import com.vtchat.dto.call.CallOffer;
import com.vtchat.dto.call.CallResponse;
import com.vtchat.dto.call.IceCandidate;
import com.vtchat.dto.message.MessageEvent;
import com.vtchat.dto.message.MessageResponse;
import com.vtchat.dto.message.WsErrorResponse;
import com.vtchat.entity.CallStatus;
import com.vtchat.entity.CallType;
import com.vtchat.exception.ApiException;
import com.vtchat.service.CallRegistry.ActiveCall;
import com.vtchat.redis.CallCommandListener;
import com.vtchat.redis.DistributedEvent;
import com.vtchat.redis.RealtimeEventBus;
import com.vtchat.service.CallRegistry.Phase;
import com.vtchat.websocket.CallEventPublisher;
import com.vtchat.websocket.MessageEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.regex.Pattern;

@Slf4j
@Service
public class CallService implements CallCommandListener {

    private static final Pattern CALL_ID = Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");
    private static final int MAX_SDP = 20_000;
    private static final int MAX_CANDIDATE = 2_000;
    private static final int MAX_SHORT = 64;

    private final AppProperties.Call config;
    private final CallRegistry registry;
    private final CallSessionStore store;
    private final CallEventPublisher events;
    private final MessageEventPublisher messageEvents;
    private final NotificationDispatcher notifier;
    private final RealtimeEventBus bus;
    private final ObjectMapper mapper;

    public CallService(AppProperties props, CallRegistry registry, CallSessionStore store,
                       CallEventPublisher events, MessageEventPublisher messageEvents, NotificationDispatcher notifier,
                       RealtimeEventBus bus, ObjectMapper mapper) {
        this.bus = bus;
        this.mapper = mapper;
        this.config = props.getCall();
        this.registry = registry;
        this.store = store;
        this.events = events;
        this.messageEvents = messageEvents;
        this.notifier = notifier;
    }

    public void offer(Long callerId, CallOffer offer) {
        if (offer == null) {
            throw invalid();
        }
        String callId = normalizeCallId(offer.callId());
        CallType type = parseType(offer.callType());
        String sdp = requireSdp(offer.sdp());

        CallSessionStore.Participants who = store.participants(offer.conversationId(), callerId);
        Long receiverId = who.receiver().id();
        if (receiverId.equals(callerId)) {
            throw ApiException.forbidden("FORBIDDEN", "You cannot call yourself");
        }
        if (store.exists(callId)) {
            throw ApiException.conflict("CALL_EXISTS", "This call already exists");
        }

        ActiveCall call = new ActiveCall(callId, who.conversationId(), callerId, receiverId, type);
        switch (registry.register(call)) {
            case CALLER_BUSY -> throw ApiException.conflict("ALREADY_IN_CALL", "You are already in a call");
            case DUPLICATE -> throw ApiException.conflict("CALL_EXISTS", "This call already exists");
            case RECEIVER_BUSY -> {
                MessageResponse record = store.recordBusy(callId, who.conversationId(), callerId, receiverId, type, Instant.now());
                CallResponse response = response(call, CallStatus.MISSED, null);
                events.toUser(callerId, CallEvent.of(CallEvent.BUSY, response));
                publishRecord(record);
                notifier.onCallFinished(callId, callerId, receiverId, true);
                return;
            }
            case OK -> { }
        }

        CallResponse opened;
        try {
            opened = store.open(callId, who.conversationId(), callerId, receiverId, type, call.startedAt);
        } catch (RuntimeException e) {
            registry.remove(call);
            throw e;
        }
        events.toUser(receiverId, CallEvent.offer(opened, who.caller(), who.receiver(), sdp));
        events.toUser(callerId, CallEvent.of(CallEvent.RINGING, opened));
        notifier.onIncomingCall(callId, callerId, receiverId);
    }

    public void answer(Long userId, CallAnswer answer) {
        String sdp = requireSdp(answer == null ? null : answer.sdp());
        if (forward("answer", userId, answer.callId(), answer)) {
            return;
        }
        ActiveCall call = require(answer.callId());
        CallResponse response;
        synchronized (call) {
            requireOpen(call);
            if (!call.receiverId.equals(userId)) {
                throw ApiException.forbidden("FORBIDDEN", "Only the person being called can answer");
            }
            if (call.phase != Phase.RINGING) {
                throw ApiException.conflict("CALL_NOT_RINGING", "This call can no longer be answered");
            }
            call.phase = Phase.ACCEPTED;
            call.answeredAt = Instant.now();
            response = response(call, call.visibleStatus(), null);
        }
        persistQuietly(() -> store.markAccepted(call.callId, call.answeredAt));
        events.toUser(call.callerId, CallEvent.answer(response, sdp));
        events.toUser(call.receiverId, CallEvent.of(CallEvent.HANDLED, response));
    }

    public void ice(Long userId, IceCandidate ice) {
        if (ice == null || ice.candidate() == null) {
            throw invalid();
        }
        IceCandidate.Candidate c = ice.candidate();
        if (c.candidate() == null || c.candidate().length() > MAX_CANDIDATE
                || (c.sdpMid() != null && c.sdpMid().length() > MAX_SHORT)
                || (c.usernameFragment() != null && c.usernameFragment().length() > MAX_SHORT)) {
            throw invalid();
        }
        if (forward("ice", userId, ice.callId(), ice)) {
            return;
        }
        ActiveCall call = require(ice.callId());
        CallResponse response;
        synchronized (call) {
            requireOpen(call);
            requireParticipant(call, userId);

            response = response(call, call.visibleStatus(), null);
        }
        events.toUser(call.other(userId), CallEvent.ice(response, c));
    }

    public void connected(Long userId, CallAction action) {
        if (action != null && forward("connected", userId, action.callId(), action)) {
            return;
        }
        ActiveCall call = require(action == null ? null : action.callId());
        synchronized (call) {
            requireOpen(call);
            requireParticipant(call, userId);
            if (call.phase != Phase.ACCEPTED) {
                return;
            }
            call.phase = Phase.CONNECTED;
            call.connectedAt = Instant.now();
        }
        persistQuietly(() -> store.markConnected(call.callId, call.connectedAt));
    }

    public void reject(Long userId, CallAction action) {
        if (action != null && forward("reject", userId, action.callId(), action)) {
            return;
        }
        ActiveCall call = require(action == null ? null : action.callId());
        synchronized (call) {
            requireOpen(call);
            if (!call.receiverId.equals(userId)) {
                throw ApiException.forbidden("FORBIDDEN", "Only the person being called can decline");
            }
            if (call.phase != Phase.RINGING) {
                throw ApiException.conflict("CALL_NOT_RINGING", "This call can no longer be declined");
            }
            finish(call, CallStatus.REJECTED, userId);
        }
    }

    public void cancel(Long userId, CallAction action) {
        if (action != null && forward("cancel", userId, action.callId(), action)) {
            return;
        }
        ActiveCall call = require(action == null ? null : action.callId());
        synchronized (call) {
            requireOpen(call);
            if (!call.callerId.equals(userId)) {
                throw ApiException.forbidden("FORBIDDEN", "Only the caller can cancel a call");
            }
            if (call.phase != Phase.RINGING) {
                throw ApiException.conflict("CALL_NOT_RINGING", "Use end to hang up an answered call");
            }
            finish(call, CallStatus.CANCELLED, userId);
        }
    }

    public void end(Long userId, CallAction action) {
        if (action != null && forward("end", userId, action.callId(), action)) {
            return;
        }
        ActiveCall call = require(action == null ? null : action.callId());
        synchronized (call) {
            requireOpen(call);
            requireParticipant(call, userId);
            boolean failed = action.reason() != null && "FAILED".equals(action.reason().toUpperCase(Locale.ROOT));
            CallStatus outcome;
            if (call.phase == Phase.RINGING) {
                outcome = call.callerId.equals(userId) ? CallStatus.CANCELLED : CallStatus.REJECTED;
            } else if (call.connectedAt != null) {
                outcome = failed ? CallStatus.FAILED : CallStatus.COMPLETED;
            } else {
                outcome = failed ? CallStatus.FAILED : CallStatus.CANCELLED;
            }
            finish(call, outcome, userId);
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void sweep() {
        Instant now = Instant.now();
        for (ActiveCall call : registry.all()) {
            if (!call.closed) {
                registry.refresh(call);
            }
            synchronized (call) {
                if (call.closed) {
                    continue;
                }
                if (call.phase == Phase.RINGING
                        && Duration.between(call.startedAt, now).getSeconds() >= config.getRingTimeoutSeconds()) {
                    finish(call, CallStatus.MISSED, null);
                } else if (call.phase == Phase.ACCEPTED && call.answeredAt != null
                        && Duration.between(call.answeredAt, now).getSeconds() >= config.getConnectTimeoutSeconds()) {
                    finish(call, CallStatus.FAILED, null);
                }
            }
        }
    }

    public void endCallsOfDisconnectedUser(Long userId) {
        ActiveCall call = registry.forUser(userId);
        if (call == null) {
            registry.callIdOfUser(userId).flatMap(registry::ownerOf)
                    .filter(owner -> !owner.equals(bus.instanceId()))
                    .ifPresent(owner -> bus.sendCallCommand(owner, "disconnected", userId, java.util.Map.of()));
            return;
        }
        synchronized (call) {
            if (call.closed) {
                return;
            }
            CallStatus outcome = call.phase == Phase.RINGING
                    ? (call.callerId.equals(userId) ? CallStatus.CANCELLED : CallStatus.MISSED)
                    : (call.connectedAt != null ? CallStatus.COMPLETED : CallStatus.FAILED);
            finish(call, outcome, userId);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void failLeftoverCalls() {
        try {
            boolean redisKnown = registry.hasOwner("00000000-0000-0000-0000-000000000000").isPresent();
            int n = redisKnown
                    ? store.failOrphans(Instant.now(), registry::hasOwner)
                    : store.failAllActive(Instant.now());
            if (n > 0) {
                log.info("Marked {} unfinished call(s) from before the restart as failed", n);
            }
        } catch (RuntimeException e) {
            log.warn("Could not clean up leftover calls: {}", e.getClass().getSimpleName());
        }
    }

    private boolean forward(String action, Long userId, String rawCallId, Object payload) {
        String callId = normalizeCallId(rawCallId);
        if (registry.get(callId) != null) {
            return false;
        }
        String owner = registry.ownerOf(callId).orElse(null);
        if (owner == null || owner.equals(bus.instanceId())) {
            return false;
        }
        if (!bus.sendCallCommand(owner, action, userId, payload)) {
            log.warn("Call action '{}' could not be forwarded to the owning instance", action);
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CALL_UNAVAILABLE", "Calls are temporarily unavailable. Please try again.");
        }
        return true;
    }

    @Override
    public void onCallCommand(DistributedEvent command) {
        Long actor = command.userId();
        try {
            switch (command.action()) {
                case "answer" -> answer(actor, mapper.treeToValue(command.payload(), CallAnswer.class));
                case "ice" -> ice(actor, mapper.treeToValue(command.payload(), IceCandidate.class));
                case "connected" -> connected(actor, mapper.treeToValue(command.payload(), CallAction.class));
                case "reject" -> reject(actor, mapper.treeToValue(command.payload(), CallAction.class));
                case "cancel" -> cancel(actor, mapper.treeToValue(command.payload(), CallAction.class));
                case "end" -> end(actor, mapper.treeToValue(command.payload(), CallAction.class));
                case "disconnected" -> endCallsOfDisconnectedUser(actor);
                default -> log.warn("Unknown forwarded call action ignored");
            }
        } catch (ApiException e) {
            bus.toUser(com.vtchat.redis.DistributedEvent.Kind.CALL_SIGNALING, actor, "/queue/errors",
                    new WsErrorResponse(e.getStatus().value(), e.getCode(), e.getMessage()));
        } catch (Exception e) {
            log.warn("Forwarded call action failed: {}", e.getClass().getSimpleName());
        }
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 90_000)
    public void sweepOrphans() {
        try {
            if (registry.hasOwner("00000000-0000-0000-0000-000000000000").isEmpty()) {
                return;
            }
            int n = store.failOrphans(Instant.now(), registry::hasOwner);
            if (n > 0) {
                log.info("Marked {} orphaned call(s) as failed", n);
            }
        } catch (RuntimeException e) {
            log.warn("Orphaned-call sweep failed: {}", e.getClass().getSimpleName());
        }
    }

    private void finish(ActiveCall call, CallStatus status, Long actorId) {
        if (call.closed) {
            return;
        }
        call.closed = true;
        Instant endedAt = Instant.now();
        int duration = call.connectedAt == null ? 0 : (int) Math.max(0, Duration.between(call.connectedAt, endedAt).getSeconds());
        registry.remove(call);

        MessageResponse record = null;
        try {
            record = store.finish(call.callId, status, endedAt, duration);
        } catch (RuntimeException e) {
            log.error("Could not save the end of call {}: {}", call.callId, e.getClass().getSimpleName());
        }

        CallResponse response = response(call, status, duration);
        switch (status) {
            case REJECTED -> {
                events.toUser(call.callerId, CallEvent.of(CallEvent.REJECT, response));
                events.toUser(call.receiverId, CallEvent.of(CallEvent.HANDLED, response));
            }
            case CANCELLED -> events.toUser(call.receiverId, CallEvent.of(CallEvent.CANCEL, response));
            case COMPLETED -> notifyOther(call, actorId, CallEvent.END, response);
            case MISSED -> {
                events.toUser(call.callerId, CallEvent.of(CallEvent.MISSED, response));
                events.toUser(call.receiverId, CallEvent.of(CallEvent.MISSED, response));
            }
            case FAILED -> {
                if (actorId == null) {
                    events.toUser(call.callerId, CallEvent.of(CallEvent.FAILED, response));
                    events.toUser(call.receiverId, CallEvent.of(CallEvent.FAILED, response));
                } else {
                    events.toUser(call.other(actorId), CallEvent.of(CallEvent.FAILED, response));
                }
            }
            default -> notifyOther(call, actorId, CallEvent.END, response);
        }
        publishRecord(record);
        notifier.onCallFinished(call.callId, call.callerId, call.receiverId, status == CallStatus.MISSED);
    }

    private void notifyOther(ActiveCall call, Long actorId, String type, CallResponse response) {
        if (actorId == null) {
            events.toUser(call.callerId, CallEvent.of(type, response));
            events.toUser(call.receiverId, CallEvent.of(type, response));
        } else {
            events.toUser(call.other(actorId), CallEvent.of(type, response));
        }
    }

    private void publishRecord(MessageResponse record) {
        if (record != null) {
            messageEvents.toConversation(MessageEvent.created(record));
            notifier.afterMessageSaved(record);
        }
    }

    private static CallResponse response(ActiveCall call, CallStatus status, Integer duration) {
        return new CallResponse(call.callId, call.conversationId, call.callerId, call.receiverId, call.type.name(),
                status.name(), call.startedAt, call.answeredAt, call.connectedAt, null, duration, null);
    }

    private ActiveCall require(String rawCallId) {
        String id = normalizeCallId(rawCallId);
        ActiveCall call = registry.get(id);
        if (call == null) {
            throw ApiException.notFound("CALL_NOT_FOUND", "This call is no longer available");
        }
        return call;
    }

    private static void requireOpen(ActiveCall call) {
        if (call.closed) {
            throw ApiException.notFound("CALL_NOT_FOUND", "This call is no longer available");
        }
    }

    private static void requireParticipant(ActiveCall call, Long userId) {
        if (!call.involves(userId)) {
            throw ApiException.notFound("CALL_NOT_FOUND", "This call is no longer available");
        }
    }

    private static String normalizeCallId(String raw) {
        String id = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        if (!CALL_ID.matcher(id).matches()) {
            throw invalid();
        }
        return id;
    }

    private static CallType parseType(String raw) {
        try {
            return CallType.valueOf(raw == null ? "" : raw.strip().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest("INVALID_CALL_TYPE", "Call type must be VOICE or VIDEO");
        }
    }

    private static String requireSdp(String sdp) {
        if (sdp == null || sdp.isBlank() || sdp.length() > MAX_SDP || !sdp.startsWith("v=0")) {
            throw invalid();
        }
        return sdp;
    }

    private static ApiException invalid() {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "INVALID_CALL", "The call request was not valid");
    }

    private void persistQuietly(Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("Could not save call progress: {}", e.getClass().getSimpleName());
        }
    }
}
