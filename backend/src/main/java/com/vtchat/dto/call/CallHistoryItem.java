package com.vtchat.dto.call;

import com.vtchat.entity.CallSession;

import java.time.Instant;

public record CallHistoryItem(String callId, Long conversationId, String callType, String direction, String status,
                              CallParty participant, Instant startedAt, Instant answeredAt, Instant endedAt,
                              Integer duration) {

    public static CallHistoryItem from(CallSession c, Long viewerId) {
        boolean outgoing = viewerId.equals(c.getCaller().getId());
        return new CallHistoryItem(c.getCallId(), c.getConversation().getId(), c.getCallType().name(),
                (outgoing ? CallDirection.OUTGOING : CallDirection.INCOMING).name(), c.getStatus().name(),
                CallParty.from(outgoing ? c.getReceiver() : c.getCaller()),
                c.getStartedAt(), c.getAnsweredAt(), c.getEndedAt(), c.getDuration() == null ? 0 : c.getDuration());
    }
}
