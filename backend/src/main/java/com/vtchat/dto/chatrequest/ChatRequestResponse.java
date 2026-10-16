package com.vtchat.dto.chatrequest;

import com.vtchat.entity.ChatRequest;

import java.time.Instant;

public record ChatRequestResponse(Long id,
                                  ChatRequestUser sender,
                                  ChatRequestUser receiver,
                                  String status,
                                  Instant createdAt,
                                  Instant updatedAt,
                                  Instant respondedAt) {

    public static ChatRequestResponse of(ChatRequest r) {
        return new ChatRequestResponse(r.getId(), ChatRequestUser.from(r.getSender()), ChatRequestUser.from(r.getReceiver()),
                r.getStatus().name(), r.getCreatedAt(), r.getUpdatedAt(), r.getRespondedAt());
    }
}
