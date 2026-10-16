package com.vtchat.controller;

import com.vtchat.dto.ApiResponse;
import com.vtchat.dto.message.DeleteMessageResponse;
import com.vtchat.dto.message.EditMessageRequest;
import com.vtchat.dto.message.ReadRequest;
import com.vtchat.dto.message.ForwardMessageRequest;
import com.vtchat.dto.message.MessageResponse;
import com.vtchat.dto.message.MessageSearchResponse;
import com.vtchat.dto.message.ReactionRequest;
import com.vtchat.dto.message.ReactionResponse;
import com.vtchat.security.AuthenticatedUser;
import com.vtchat.service.MessageActionService;
import com.vtchat.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final MessageActionService actions;

    @GetMapping("/api/conversations/{conversationId}/messages")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> history(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                      @PathVariable Long conversationId,
                                                                      @RequestParam(value = "aroundId", required = false) Long aroundId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", messageService.history(conversationId, principal.id(), aroundId)));
    }

    @PutMapping("/api/conversations/{conversationId}/read")
    public ResponseEntity<ApiResponse<com.vtchat.dto.conversation.ConversationStateResponse>> markRead(
            @AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long conversationId,
            @RequestBody(required = false) ReadRequest request) {
        Long upTo = request == null ? null : request.lastReadMessageId();
        return ResponseEntity.ok(ApiResponse.ok("OK", actions.markRead(principal.id(), conversationId, upTo)));
    }

    @GetMapping("/api/conversations/{conversationId}/messages/search")
    public ResponseEntity<ApiResponse<List<MessageSearchResponse>>> search(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                           @PathVariable Long conversationId,
                                                                           @RequestParam("query") String query) {
        return ResponseEntity.ok(ApiResponse.ok("OK", messageService.search(conversationId, principal.id(), query)));
    }

    @PutMapping("/api/messages/{messageId}")
    public ResponseEntity<ApiResponse<MessageResponse>> edit(@AuthenticationPrincipal AuthenticatedUser principal,
                                                             @PathVariable Long messageId,
                                                             @RequestBody EditMessageRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Message updated", actions.edit(principal.id(), messageId, request.content())));
    }

    @PostMapping("/api/messages/{messageId}/reactions")
    public ResponseEntity<ApiResponse<List<ReactionResponse>>> react(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                     @PathVariable Long messageId,
                                                                     @RequestBody ReactionRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Reaction saved", actions.react(principal.id(), messageId, request.reaction())));
    }

    @DeleteMapping("/api/messages/{messageId}/reactions")
    public ResponseEntity<ApiResponse<List<ReactionResponse>>> unreact(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                       @PathVariable Long messageId) {
        return ResponseEntity.ok(ApiResponse.ok("Reaction removed", actions.unreact(principal.id(), messageId)));
    }

    @DeleteMapping("/api/messages/{messageId}/for-me")
    public ResponseEntity<ApiResponse<DeleteMessageResponse>> deleteForMe(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                          @PathVariable Long messageId) {
        return ResponseEntity.ok(ApiResponse.ok("Message deleted for you", actions.deleteForMe(principal.id(), messageId)));
    }

    @DeleteMapping("/api/messages/{messageId}/for-everyone")
    public ResponseEntity<ApiResponse<DeleteMessageResponse>> deleteForEveryone(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                                @PathVariable Long messageId) {
        return ResponseEntity.ok(ApiResponse.ok("Message deleted for everyone", actions.deleteForEveryone(principal.id(), messageId)));
    }

    @PostMapping("/api/messages/{messageId}/forward")
    public ResponseEntity<ApiResponse<MessageResponse>> forward(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                @PathVariable Long messageId,
                                                                @RequestBody ForwardMessageRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("Message forwarded", actions.forward(principal.id(), messageId, request.conversationId())));
    }
}
