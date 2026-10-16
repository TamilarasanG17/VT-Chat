package com.vtchat.dto.message;

import com.vtchat.entity.MessageType;

import java.time.Instant;

public record MessageSearchResponse(Long id, Long conversationId, Long senderId, String senderUsername,
                                    String content, MessageType messageType, String mediaName, Instant createdAt) {
}
