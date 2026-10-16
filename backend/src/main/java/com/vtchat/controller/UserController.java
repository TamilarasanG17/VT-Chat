package com.vtchat.controller;

import com.vtchat.dto.ApiResponse;
import com.vtchat.dto.presence.PrivacyRequest;
import com.vtchat.dto.user.ChangePasswordRequest;
import com.vtchat.dto.user.UpdateProfileRequest;
import com.vtchat.dto.user.UserResponse;
import com.vtchat.security.AuthenticatedUser;
import com.vtchat.service.PresenceService;
import com.vtchat.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/users/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final PresenceService presenceService;

    @GetMapping
    public ResponseEntity<ApiResponse<UserResponse>> getMe(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok("OK", userService.getMe(principal.id())));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<UserResponse>> updateProfile(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                   @Valid @RequestBody UpdateProfileRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Profile updated successfully",
                userService.updateProfile(principal.id(), req)));
    }

    @PostMapping(value = "/profile-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<UserResponse>> uploadProfilePicture(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                          @RequestParam("file") MultipartFile file) {
        return ResponseEntity.ok(ApiResponse.ok("Profile picture updated successfully",
                userService.uploadProfilePicture(principal.id(), file)));
    }

    @DeleteMapping("/profile-picture")
    public ResponseEntity<ApiResponse<UserResponse>> removeProfilePicture(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok("Profile picture removed",
                userService.removeProfilePicture(principal.id())));
    }

    @PutMapping("/password")
    public ResponseEntity<ApiResponse<Map<String, Object>>> changePassword(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                           @Valid @RequestBody ChangePasswordRequest req) {
        userService.changePassword(principal.id(), principal.sessionId(), req);
        return ResponseEntity.ok(ApiResponse.ok("Password changed successfully"));
    }

    @PutMapping("/privacy")
    public ResponseEntity<ApiResponse<UserResponse>> updatePrivacy(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                   @Valid @RequestBody PrivacyRequest req) {
        return ResponseEntity.ok(ApiResponse.ok("Privacy settings updated",
                presenceService.updatePrivacy(principal.id(), req.showOnlineStatus(), req.showLastSeen())));
    }
}
