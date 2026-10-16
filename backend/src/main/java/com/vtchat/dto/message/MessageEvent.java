package com.vtchat.dto.message;

import java.util.List;

public record MessageEvent(String type, Long conversationId, Long messageId, MessageResponse message,
                           List<ReactionResponse> reactions) {

    public static final String CREATED = "MESSAGE_CREATED";
    public static final String UPDATED = "MESSAGE_UPDATED";
    public static final String DELETED_FOR_ME = "MESSAGE_DELETED_FOR_ME";
    public static final String DELETED_FOR_EVERYONE = "MESSAGE_DELETED_FOR_EVERYONE";
    public static final String REACTION_UPDATED = "REACTION_UPDATED";

    public static MessageEvent created(MessageResponse m) {
        return new MessageEvent(CREATED, m.conversationId(), m.id(), m, null);
    }

    public static MessageEvent updated(MessageResponse m) {
        return new MessageEvent(UPDATED, m.conversationId(), m.id(), m, null);
    }

    public static MessageEvent deletedForEveryone(MessageResponse m) {
        return new MessageEvent(DELETED_FOR_EVERYONE, m.conversationId(), m.id(), m, null);
    }

    public static MessageEvent deletedForMe(Long conversationId, Long messageId) {
        return new MessageEvent(DELETED_FOR_ME, conversationId, messageId, null, null);
    }

    public static MessageEvent reactionUpdated(Long conversationId, Long messageId, List<ReactionResponse> reactions) {
        return new MessageEvent(REACTION_UPDATED, conversationId, messageId, null, reactions);
    }
}
