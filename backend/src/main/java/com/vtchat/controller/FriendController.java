package com.vtchat.controller;

import com.vtchat.dto.ApiResponse;
import com.vtchat.dto.chatrequest.ChatRequestStatusResponse;
import com.vtchat.security.AuthenticatedUser;
import com.vtchat.service.ChatRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/friends")
@RequiredArgsConstructor
public class FriendController {

    private final ChatRequestService service;

    @DeleteMapping("/{userId}")
    public ResponseEntity<ApiResponse<ChatRequestStatusResponse>> unfriend(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                           @PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.ok("Connection ended", service.unfriend(principal.id(), userId)));
    }
}
