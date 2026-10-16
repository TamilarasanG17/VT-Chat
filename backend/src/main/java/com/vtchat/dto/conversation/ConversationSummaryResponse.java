package com.vtchat.dto.conversation;

import com.vtchat.dto.user.UserResponse;
import com.vtchat.entity.Conversation;
import com.vtchat.entity.Message;
import com.vtchat.entity.MessageType;
import com.vtchat.entity.User;

import com.vtchat.util.TextUtil;

import java.time.Instant;

public record ConversationSummaryResponse(Long id, Participant otherUser, LastMessage lastMessage, Instant updatedAt,
                                          int unreadCount, boolean muted, boolean pinned, boolean archived,
                                          boolean messagingEnabled) {

    private static final int PREVIEW_LENGTH = 100;

    public record Participant(Long id, String username, String profilePictureUrl) {
        static Participant from(User user) {
            return new Participant(user.getId(), user.getUsername(), UserResponse.avatarUrl(user));
        }
    }

    public record LastMessage(Long id, Long senderId, String content, MessageType messageType, String mediaName,
                              String callStatus, Integer duration, boolean deleted, Instant createdAt) {
        static LastMessage from(Message message) {
            return new LastMessage(message.getId(), message.getSender().getId(),
                    message.isDeleted() ? null : TextUtil.truncate(message.getContent(), PREVIEW_LENGTH),
                    message.getMessageType(),
                    message.isDeleted() ? null : message.getMediaName(),
                    message.isDeleted() ? null : message.getCallStatus(),
                    message.isDeleted() ? null : message.getDuration(),
                    message.isDeleted(), message.getCreatedAt());
        }
    }

    public static ConversationSummaryResponse of(Conversation conversation, Long currentUserId, Message latest,
                                                 com.vtchat.entity.ConversationParticipantState state) {
        return new ConversationSummaryResponse(
                conversation.getId(),
                Participant.from(conversation.otherParticipant(currentUserId)),
                latest == null ? null : LastMessage.from(latest),
                conversation.getUpdatedAt(),
                state == null ? 0 : state.getUnreadCount(),
                state != null && state.isMuted(),
                state != null && state.isPinned(),
                state != null && state.isArchived(),
                true);
    }

    public ConversationSummaryResponse withMessagingEnabled(boolean enabled) {
        return new ConversationSummaryResponse(id, otherUser, lastMessage, updatedAt, unreadCount, muted, pinned, archived, enabled);
    }
}
