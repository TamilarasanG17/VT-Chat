package com.vtchat.dto.conversation;

public record ConversationStateEvent(String type, Long conversationId, int unreadCount, boolean muted, boolean pinned,
                                     boolean archived, boolean deleted) {

    public static final String STATE = "CONVERSATION_STATE";

    public static ConversationStateEvent of(ConversationStateResponse s) {
        return new ConversationStateEvent(STATE, s.conversationId(), s.unreadCount(), s.muted(), s.pinned(), s.archived(), s.deleted());
    }
}
