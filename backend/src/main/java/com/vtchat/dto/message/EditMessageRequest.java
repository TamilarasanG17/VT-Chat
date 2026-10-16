package com.vtchat.dto.message;

public record EditMessageRequest(Long messageId, String content) {
}
