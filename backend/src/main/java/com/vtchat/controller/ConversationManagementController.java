package com.vtchat.controller;

import com.vtchat.dto.ApiResponse;
import com.vtchat.dto.conversation.ConversationStateResponse;
import com.vtchat.dto.conversation.ToggleRequest;
import com.vtchat.security.AuthenticatedUser;
import com.vtchat.service.ConversationActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/conversations/{conversationId}")
@RequiredArgsConstructor
public class ConversationManagementController {

    private final ConversationActionService actions;

    @PutMapping("/pin")
    public ResponseEntity<ApiResponse<ConversationStateResponse>> pin(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                     @PathVariable Long conversationId,
                                                                     @RequestBody(required = false) ToggleRequest body) {
        return ok(actions.pin(principal.id(), conversationId, body == null || body.value()));
    }

    @PutMapping("/mute")
    public ResponseEntity<ApiResponse<ConversationStateResponse>> mute(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                      @PathVariable Long conversationId,
                                                                      @RequestBody(required = false) ToggleRequest body) {
        return ok(actions.mute(principal.id(), conversationId, body == null || body.value()));
    }

    @PutMapping("/archive")
    public ResponseEntity<ApiResponse<ConversationStateResponse>> archive(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                         @PathVariable Long conversationId) {
        return ok(actions.archive(principal.id(), conversationId, true));
    }

    @PutMapping("/unarchive")
    public ResponseEntity<ApiResponse<ConversationStateResponse>> unarchive(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                           @PathVariable Long conversationId) {
        return ok(actions.archive(principal.id(), conversationId, false));
    }

    @DeleteMapping("/messages")
    public ResponseEntity<ApiResponse<ConversationStateResponse>> clear(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                       @PathVariable Long conversationId) {
        return ok(actions.clear(principal.id(), conversationId));
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<ConversationStateResponse>> delete(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                        @PathVariable Long conversationId) {
        return ok(actions.delete(principal.id(), conversationId));
    }

    private static ResponseEntity<ApiResponse<ConversationStateResponse>> ok(ConversationStateResponse state) {
        return ResponseEntity.ok(ApiResponse.ok("OK", state));
    }
}
