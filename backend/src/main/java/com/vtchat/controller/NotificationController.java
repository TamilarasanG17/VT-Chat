package com.vtchat.controller;

import com.vtchat.dto.ApiResponse;
import com.vtchat.dto.notification.NotificationEvent;
import com.vtchat.dto.notification.NotificationPageResponse;
import com.vtchat.dto.notification.UnreadCountResponse;
import com.vtchat.security.AuthenticatedUser;
import com.vtchat.service.NotificationService;
import com.vtchat.websocket.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notifications;
    private final NotificationEventPublisher publisher;

    @GetMapping
    public ResponseEntity<ApiResponse<NotificationPageResponse>> list(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                      @RequestParam(defaultValue = "0") int page,
                                                                      @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.ok("OK", notifications.page(principal.id(), page, size)));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> unreadCount(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok("OK", new UnreadCountResponse(notifications.unreadCount(principal.id()))));
    }

    @PutMapping("/{notificationId}/read")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> markRead(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                     @PathVariable Long notificationId) {
        long unread = notifications.markRead(principal.id(), notificationId);
        publisher.notification(principal.id(), NotificationEvent.updated(unread, null));
        return ResponseEntity.ok(ApiResponse.ok("OK", new UnreadCountResponse(unread)));
    }

    @PutMapping("/read-all")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> markAllRead(@AuthenticationPrincipal AuthenticatedUser principal) {
        long unread = notifications.markAllRead(principal.id());
        publisher.notification(principal.id(), NotificationEvent.updated(unread, null));
        return ResponseEntity.ok(ApiResponse.ok("OK", new UnreadCountResponse(unread)));
    }

    @DeleteMapping("/{notificationId}")
    public ResponseEntity<ApiResponse<UnreadCountResponse>> delete(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                  @PathVariable Long notificationId) {
        long unread = notifications.delete(principal.id(), notificationId);
        publisher.notification(principal.id(), NotificationEvent.updated(unread, null));
        return ResponseEntity.ok(ApiResponse.ok("Notification deleted", new UnreadCountResponse(unread)));
    }
}
