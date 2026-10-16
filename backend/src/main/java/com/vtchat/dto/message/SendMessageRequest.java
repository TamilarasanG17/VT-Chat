package com.vtchat.dto.message;

public record SendMessageRequest(Long conversationId, String content, Long replyToMessageId) {
}
