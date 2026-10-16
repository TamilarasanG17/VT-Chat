package com.vtchat.dto.user;

import com.vtchat.storage.CloudinaryRef;
import com.vtchat.entity.User;

import java.time.Instant;

public record UserResponse(Long id,
                           String username,
                           String email,
                           boolean emailVerified,
                           String profilePictureUrl,
                           String bio,
                           boolean showOnlineStatus,
                           boolean showLastSeen,
                           Instant createdAt) {

    public static final String AVATAR_URL_PREFIX = "/api/avatars/";

    public static UserResponse from(User user) {
        return new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.isEmailVerified(),
                avatarUrl(user),
                user.getBio(),
                user.isShowOnlineStatus(),
                user.isShowLastSeen(),
                user.getCreatedAt());
    }

    public static String avatarUrl(User user) {
        String picture = user.getProfilePicture();
        if (picture == null) {
            return null;
        }
        if (CloudinaryRef.isRef(picture)) {
            return CloudinaryRef.parse(picture)
                    .map(r -> AVATAR_URL_PREFIX + "c/" + r.tail().substring("avatars/".length()))
                    .orElse(null);
        }
        return AVATAR_URL_PREFIX + (picture.startsWith("avatars/") ? picture.substring("avatars/".length()) : picture);
    }
}
