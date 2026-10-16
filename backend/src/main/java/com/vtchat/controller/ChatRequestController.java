package com.vtchat.controller;

import com.vtchat.dto.ApiResponse;
import com.vtchat.dto.chatrequest.ChatRequestResponse;
import com.vtchat.dto.chatrequest.ChatRequestResult;
import com.vtchat.dto.chatrequest.ChatRequestStatusResponse;
import com.vtchat.security.AuthenticatedUser;
import com.vtchat.service.ChatRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat-requests")
@RequiredArgsConstructor
public class ChatRequestController {

    private final ChatRequestService service;

    @PostMapping("/{receiverId}")
    public ResponseEntity<ApiResponse<ChatRequestResult>> send(@AuthenticationPrincipal AuthenticatedUser principal,
                                                               @PathVariable Long receiverId) {
        return ResponseEntity.ok(ApiResponse.ok("Chat request sent", service.send(principal.id(), receiverId)));
    }

    @GetMapping("/received")
    public ResponseEntity<ApiResponse<List<ChatRequestResponse>>> received(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok("OK", service.received(principal.id())));
    }

    @GetMapping("/received/count")
    public ResponseEntity<ApiResponse<Map<String, Long>>> receivedCount(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok("OK", Map.of("count", service.receivedCount(principal.id()))));
    }

    @GetMapping("/sent")
    public ResponseEntity<ApiResponse<List<ChatRequestResponse>>> sent(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok("OK", service.sent(principal.id())));
    }

    @GetMapping("/status/{userId}")
    public ResponseEntity<ApiResponse<ChatRequestStatusResponse>> status(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                         @PathVariable Long userId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", service.status(principal.id(), userId)));
    }

    @PutMapping("/{requestId}/accept")
    public ResponseEntity<ApiResponse<ChatRequestResult>> accept(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                 @PathVariable Long requestId) {
        return ResponseEntity.ok(ApiResponse.ok("Chat request accepted", service.accept(principal.id(), requestId)));
    }

    @PutMapping("/{requestId}/decline")
    public ResponseEntity<ApiResponse<ChatRequestResult>> decline(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                  @PathVariable Long requestId) {
        return ResponseEntity.ok(ApiResponse.ok("Chat request declined", service.decline(principal.id(), requestId)));
    }
}
