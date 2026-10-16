package com.vtchat.dto.message;

import com.vtchat.entity.MessageType;

import java.time.Instant;
import java.util.List;

public record MessageResponse(
        Long id,
        Long conversationId,
        Long senderId,
        String content,
        boolean edited,
        boolean deleted,
        Instant deletedAt,
        ReplyPreview replyToMessage,
        List<ReactionResponse> reactions,
        boolean forwarded,
        Long forwardedFromMessageId,
        MessageType messageType,
        String mediaUrl,
        String mediaName,
        Long mediaSize,
        String mimeType,
        Integer duration,
        String callId,
        String callStatus,
        Boolean downloadableByCurrentUser,
        Boolean downloadedByCurrentUser,
        String deliveryStatus,
        Instant createdAt,
        Instant updatedAt) {

    public record ReplyPreview(Long id, Long senderId, String content, MessageType messageType, String mediaName,
                               boolean deleted, boolean unavailable) {
    }
}
