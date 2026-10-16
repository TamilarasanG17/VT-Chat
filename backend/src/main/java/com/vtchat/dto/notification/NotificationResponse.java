package com.vtchat.dto.notification;

import com.vtchat.entity.Notification;

import java.time.Instant;

public record NotificationResponse(Long id, String type, String title, String message, Long conversationId,
                                   Long messageId, String callId, boolean read, Instant createdAt) {

    public static NotificationResponse of(Notification n) {
        return new NotificationResponse(n.getId(), n.getType().name(), n.getTitle(), n.getMessage(),
                n.getConversation() == null ? null : n.getConversation().getId(),
                n.getMessageReference() == null ? null : n.getMessageReference().getId(),
                n.getCallReference() == null ? null : n.getCallReference().getCallId(),
                n.isRead(), n.getCreatedAt());
    }
}
