package com.vtchat.dto.call;

import com.vtchat.dto.user.UserResponse;
import com.vtchat.entity.User;

public record CallParty(Long id, String username, String profilePictureUrl) {

    public static CallParty from(User user) {
        return new CallParty(user.getId(), user.getUsername(), UserResponse.avatarUrl(user));
    }
}
