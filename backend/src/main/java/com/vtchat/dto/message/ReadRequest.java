package com.vtchat.dto.message;

public record ReadRequest(Long conversationId, Long lastReadMessageId) {
}
