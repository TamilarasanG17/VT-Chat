package com.vtchat.dto.presence;

public record TypingEvent(Long conversationId, Long userId, boolean typing) {
}
