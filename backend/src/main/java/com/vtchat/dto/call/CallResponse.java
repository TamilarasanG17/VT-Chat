package com.vtchat.dto.call;

import com.vtchat.entity.CallSession;

import java.time.Instant;

public record CallResponse(String callId, Long conversationId, Long callerId, Long receiverId, String callType,
                           String status, Instant startedAt, Instant answeredAt, Instant connectedAt, Instant endedAt,
                           Integer duration, Instant createdAt) {

    public static CallResponse from(CallSession c) {
        return new CallResponse(c.getCallId(), c.getConversation().getId(), c.getCaller().getId(), c.getReceiver().getId(),
                c.getCallType().name(), c.getStatus().name(), c.getStartedAt(), c.getAnsweredAt(), c.getConnectedAt(),
                c.getEndedAt(), c.getDuration(), c.getCreatedAt());
    }
}
