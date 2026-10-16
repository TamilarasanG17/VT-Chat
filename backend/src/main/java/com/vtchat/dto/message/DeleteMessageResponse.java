package com.vtchat.dto.message;

public record DeleteMessageResponse(Long messageId, String scope, MessageResponse message) {
}
