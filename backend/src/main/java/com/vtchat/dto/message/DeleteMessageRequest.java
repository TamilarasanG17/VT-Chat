package com.vtchat.dto.message;

public record DeleteMessageRequest(Long messageId, String scope) {
}
