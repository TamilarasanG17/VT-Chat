package com.vtchat.dto.chatrequest;

import com.vtchat.dto.user.UserResponse;
import com.vtchat.entity.User;

public record ChatRequestUser(Long id, String username, String profilePictureUrl) {

    public static ChatRequestUser from(User u) {
        return new ChatRequestUser(u.getId(), u.getUsername(), UserResponse.avatarUrl(u));
    }
}
