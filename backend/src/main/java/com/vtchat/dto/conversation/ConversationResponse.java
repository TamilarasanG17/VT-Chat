package com.vtchat.dto.conversation;

import com.vtchat.dto.user.UserSearchResponse;
import com.vtchat.entity.Conversation;

import java.time.Instant;

public record ConversationResponse(Long id,
                                   UserSearchResponse currentUser,
                                   UserSearchResponse otherUser,
                                   Instant createdAt,
                                   Instant updatedAt,
                                   boolean messagingEnabled) {

    public static ConversationResponse of(Conversation conversation, Long currentUserId) {
        return new ConversationResponse(
                conversation.getId(),
                UserSearchResponse.from(currentUserId.equals(conversation.getUserOne().getId())
                        ? conversation.getUserOne() : conversation.getUserTwo()),
                UserSearchResponse.from(conversation.otherParticipant(currentUserId)),
                conversation.getCreatedAt(),
                conversation.getUpdatedAt(),
                true);
    }

    public ConversationResponse withMessagingEnabled(boolean enabled) {
        return new ConversationResponse(id, currentUser, otherUser, createdAt, updatedAt, enabled);
    }
}
