package com.vtchat.dto.presence;

public record TypingRequest(Long conversationId, boolean typing) {
}
