package com.vtchat.controller;

import com.vtchat.dto.ApiResponse;
import com.vtchat.dto.presence.PresenceResponse;
import com.vtchat.security.AuthenticatedUser;
import com.vtchat.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class PresenceController {

    private final PresenceService presence;

    @GetMapping("/api/users/{userId}/presence")
    public ResponseEntity<ApiResponse<PresenceResponse>> one(@AuthenticationPrincipal AuthenticatedUser principal,
                                                             @PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", presence.getPresence(principal.id(), userId)));
    }

    @GetMapping("/api/presence")
    public ResponseEntity<ApiResponse<List<PresenceResponse>>> partners(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok("OK", presence.getPeersPresence(principal.id())));
    }
}
