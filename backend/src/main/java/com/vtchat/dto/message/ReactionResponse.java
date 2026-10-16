package com.vtchat.dto.message;

import com.vtchat.entity.MessageReaction;

public record ReactionResponse(Long userId, String reaction) {

    public static ReactionResponse from(MessageReaction r) {
        return new ReactionResponse(r.getUser().getId(), r.getReaction());
    }
}
