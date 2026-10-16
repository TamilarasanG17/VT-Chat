package com.vtchat.controller;

import com.vtchat.dto.ApiResponse;
import com.vtchat.dto.notification.NotificationSettingsRequest;
import com.vtchat.dto.notification.NotificationSettingsResponse;
import com.vtchat.security.AuthenticatedUser;
import com.vtchat.service.NotificationSettingsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users/me/notification-settings")
@RequiredArgsConstructor
public class NotificationSettingsController {

    private final NotificationSettingsService settings;

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationSettingsResponse>> get(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok("OK", settings.view(principal.id())));
    }

    @PutMapping
    public ResponseEntity<ApiResponse<NotificationSettingsResponse>> update(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                           @RequestBody NotificationSettingsRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Notification settings updated", settings.update(principal.id(), request)));
    }
}
