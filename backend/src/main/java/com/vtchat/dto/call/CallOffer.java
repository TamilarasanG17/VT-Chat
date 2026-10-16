package com.vtchat.dto.call;

public record CallOffer(String callId, Long conversationId, String callType, String sdp) {
}
