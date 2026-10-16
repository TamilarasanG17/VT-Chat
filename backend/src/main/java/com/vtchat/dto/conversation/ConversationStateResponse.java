package com.vtchat.dto.conversation;

public record ConversationStateResponse(Long conversationId, int unreadCount, boolean muted, boolean pinned,
                                        boolean archived, boolean deleted, Long lastReadMessageId) {
}
