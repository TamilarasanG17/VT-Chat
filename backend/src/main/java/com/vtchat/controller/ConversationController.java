package com.vtchat.controller;

import com.vtchat.dto.ApiResponse;
import com.vtchat.dto.conversation.ConversationResponse;
import com.vtchat.dto.conversation.ConversationSummaryResponse;
import com.vtchat.dto.conversation.CreateConversationRequest;
import com.vtchat.security.AuthenticatedUser;
import com.vtchat.service.ConversationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;

    @PostMapping
    public ResponseEntity<ApiResponse<ConversationResponse>> start(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                   @Valid @RequestBody CreateConversationRequest req) {
        ConversationService.StartResult result = conversationService.start(principal.id(), req.userId());
        return ResponseEntity
                .status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
                .body(ApiResponse.ok(result.created() ? "Conversation created" : "Conversation already exists",
                        result.conversation()));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ConversationSummaryResponse>>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok("OK", conversationService.list(principal.id())));
    }

    @GetMapping("/{conversationId}")
    public ResponseEntity<ApiResponse<ConversationResponse>> get(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                 @PathVariable Long conversationId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", conversationService.get(conversationId, principal.id())));
    }
}
