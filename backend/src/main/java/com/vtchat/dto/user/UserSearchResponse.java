package com.vtchat.dto.user;

import com.vtchat.entity.User;

public record UserSearchResponse(Long id,
                                 String username,
                                 String email,
                                 boolean emailVerified,
                                 String profilePictureUrl,
                                 String bio) {

    public static UserSearchResponse from(User user) {
        return new UserSearchResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.isEmailVerified(),
                UserResponse.avatarUrl(user),
                user.getBio());
    }
}
