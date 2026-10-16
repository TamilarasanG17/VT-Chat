package com.vtchat.service;

import com.vtchat.dto.user.ChangePasswordRequest;
import com.vtchat.dto.user.UpdateProfileRequest;
import com.vtchat.dto.user.UserResponse;
import com.vtchat.dto.user.UserSearchResponse;
import com.vtchat.entity.User;
import com.vtchat.exception.ApiException;
import com.vtchat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(noRollbackFor = ApiException.class)
public class UserService {

    private static final int MIN_SEARCH_LENGTH = 2;
    private static final int MAX_SEARCH_LENGTH = 50;
    private static final int MAX_SEARCH_RESULTS = 20;

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AvatarStorageService avatarStorage;
    private final TokenService tokenService;
    private final UserCache userCache;

    @Transactional(readOnly = true, noRollbackFor = ApiException.class)
    public UserResponse getMe(Long userId) {
        return userCache.profile(userId, () -> java.util.Optional.of(UserResponse.from(find(userId)))).orElseThrow();
    }

    @Transactional(readOnly = true, noRollbackFor = ApiException.class)
    public List<UserSearchResponse> searchUsers(Long currentUserId, String rawQuery) {
        String query = rawQuery == null ? "" : rawQuery.trim();
        if (query.length() < MIN_SEARCH_LENGTH) {
            throw ApiException.badRequest("QUERY_TOO_SHORT", "Type at least " + MIN_SEARCH_LENGTH + " characters to search");
        }
        if (query.length() > MAX_SEARCH_LENGTH) {
            throw ApiException.badRequest("QUERY_TOO_LONG", "Search text is too long");
        }
        return userCache.search(currentUserId, query, () -> users.search(currentUserId, likePattern(query), PageRequest.of(0, MAX_SEARCH_RESULTS)).stream()
                .map(UserSearchResponse::from)
                .toList());
    }

    private static String likePattern(String query) {
        String escaped = query.toLowerCase(Locale.ROOT)
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
        return "%" + escaped + "%";
    }

    public UserResponse updateProfile(Long userId, UpdateProfileRequest req) {
        User user = find(userId);
        String username = req.username();
        if (!username.equalsIgnoreCase(user.getUsername()) && users.existsByUsernameTaken(userId, username.toLowerCase(Locale.ROOT))) {
            throw ApiException.conflict("USERNAME_TAKEN", "That username is already taken");
        }
        user.setUsername(username);
        user.setBio(req.bio());
        UserResponse saved = UserResponse.from(users.save(user));
        userCache.invalidate(userId);
        return saved;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UserResponse uploadProfilePicture(Long userId, MultipartFile file) {
        User user = find(userId);
        String newRef = avatarStorage.store(file, userId);
        String oldRef = user.getProfilePicture();
        int changed;
        try {
            changed = oldRef == null ? users.setProfilePictureIfNone(userId, newRef) : users.replaceProfilePicture(userId, oldRef, newRef);
        } catch (RuntimeException e) {
            avatarStorage.delete(newRef);
            throw e;
        }
        userCache.invalidate(userId);
        if (changed == 0) {
            avatarStorage.delete(newRef);
            throw ApiException.conflict("AVATAR_CHANGED", "Your profile picture was changed elsewhere. Please try again.");
        }
        deleteIfUnreferenced(oldRef);
        return UserResponse.from(find(userId));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UserResponse removeProfilePicture(Long userId) {
        User user = find(userId);
        String oldRef = user.getProfilePicture();
        if (oldRef != null) {
            if (users.clearProfilePicture(userId, oldRef) > 0) {
                userCache.invalidate(userId);
                deleteIfUnreferenced(oldRef);
            }
        }
        return UserResponse.from(find(userId));
    }

    private void deleteIfUnreferenced(String ref) {
        if (ref != null && users.countByProfilePicture(ref) == 0) {
            avatarStorage.delete(ref);
        }
    }

    public void changePassword(Long userId, String currentSessionId, ChangePasswordRequest req) {
        User user = find(userId);

        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("INVALID_CURRENT_PASSWORD", "Your current password is incorrect");
        }
        if (passwordEncoder.matches(req.newPassword(), user.getPasswordHash())) {
            throw ApiException.badRequest("PASSWORD_UNCHANGED", "Your new password must be different from the current one");
        }

        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
        users.save(user);
        tokenService.revokeOtherSessions(userId, currentSessionId);
    }

    private User find(Long userId) {
        return users.findById(userId)
                .orElseThrow(() -> ApiException.unauthorized("UNAUTHORIZED", "Authentication required"));
    }
}
