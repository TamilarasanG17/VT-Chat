package com.vtchat.dto;

import com.vtchat.dto.user.UserResponse;
import com.vtchat.entity.User;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserResponseAvatarUrlTest {

    private static final String UUID = "3f2c1a9e-7b4d-4c1e-9a55-0123456789ab";

    private static User withPicture(String picture) {
        User u = new User();
        u.setProfilePicture(picture);
        return u;
    }

    @Test
    void noPictureMeansTheDefaultAvatar() {
        assertNull(UserResponse.avatarUrl(withPicture(null)));
    }

    @Test
    void everyReferenceKindMapsToARelativeApiUrl() {
        assertEquals("/api/avatars/" + UUID + ".jpg", UserResponse.avatarUrl(withPicture(UUID + ".jpg")));
        assertEquals("/api/avatars/7/" + UUID + ".jpg", UserResponse.avatarUrl(withPicture("avatars/7/" + UUID + ".jpg")));
        assertEquals("/api/avatars/c/7/" + UUID + ".jpg", UserResponse.avatarUrl(withPicture("cld:iu:vtchat/avatars/7/" + UUID + ".jpg")));
    }

    @Test
    void aTamperedCloudinaryReferenceYieldsNoUrl() {
        assertNull(UserResponse.avatarUrl(withPicture("cld:iu:../avatars/7/" + UUID + ".jpg")));
    }
}
