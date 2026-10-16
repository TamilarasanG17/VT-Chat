package com.vtchat.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vtchat.ai.StreamAbortedException;
import com.vtchat.dto.ApiResponse;
import com.vtchat.dto.ai.AiDtos.ChatRequest;
import com.vtchat.dto.ai.AiDtos.ChatResponse;
import com.vtchat.dto.ai.AiDtos.ConversationResponse;
import com.vtchat.dto.ai.AiDtos.CreateConversationRequest;
import com.vtchat.dto.ai.AiDtos.MessageResponse;
import com.vtchat.dto.ai.AiDtos.RagSendRequest;
import com.vtchat.dto.ai.AiDtos.SendMessageRequest;
import com.vtchat.dto.ai.AiDtos.SendMessageResponse;
import com.vtchat.exception.ApiException;
import com.vtchat.security.AuthenticatedUser;
import com.vtchat.service.AiChatService;
import com.vtchat.service.AiConversationStore;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiChatService chatService;
    private final AiConversationStore store;
    private final ObjectMapper mapper;

    @PostMapping("/chat")
    public ResponseEntity<ApiResponse<ChatResponse>> chat(@AuthenticationPrincipal AuthenticatedUser principal,
                                                          @Valid @RequestBody ChatRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("OK", chatService.chat(principal.id(), request.message())));
    }

    @PostMapping("/conversations")
    public ResponseEntity<ApiResponse<ConversationResponse>> create(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                    @Valid @RequestBody(required = false) CreateConversationRequest request) {
        ConversationResponse created = store.create(principal.id(), request == null ? null : request.title());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Created", created));
    }

    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<List<ConversationResponse>>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok("OK", store.list(principal.id())));
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<ApiResponse<ConversationResponse>> get(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                 @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("OK", store.get(principal.id(), id)));
    }

    @DeleteMapping("/conversations/{id}")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> delete(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                             @PathVariable Long id) {
        store.delete(principal.id(), id);
        return ResponseEntity.ok(ApiResponse.ok("Deleted"));
    }

    @GetMapping("/conversations/{id}/messages")
    public ResponseEntity<ApiResponse<List<MessageResponse>>> messages(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                       @PathVariable Long id,
                                                                       @RequestParam(required = false) Long before,
                                                                       @RequestParam(defaultValue = "100") int limit) {
        return ResponseEntity.ok(ApiResponse.ok("OK", store.messages(principal.id(), id, before, limit)));
    }

    @PostMapping("/conversations/{id}/messages")
    public ResponseEntity<ApiResponse<SendMessageResponse>> send(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                 @PathVariable Long id,
                                                                 @Valid @RequestBody SendMessageRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("OK", chatService.sendMessage(principal.id(), id, request.message())));
    }

    @PostMapping("/conversations/{id}/messages/stream")
    public void stream(@AuthenticationPrincipal AuthenticatedUser principal,
                       @PathVariable Long id,
                       @Valid @RequestBody SendMessageRequest request,
                       HttpServletResponse response) {
        SseWriter writer = new SseWriter(response, mapper);
        try {
            chatService.streamMessage(principal.id(), id, request.message(), writer);
        } catch (ApiException e) {
            if (!writer.started) {
                throw e;
            }
            writer.error(e);
        }
    }

    @PostMapping("/conversations/{id}/rag")
    public ResponseEntity<ApiResponse<SendMessageResponse>> rag(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                @PathVariable Long id,
                                                                @Valid @RequestBody RagSendRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("OK", chatService.sendRag(principal.id(), id, request.message(), request.documentIds())));
    }

    @PostMapping("/conversations/{id}/rag/stream")
    public void ragStream(@AuthenticationPrincipal AuthenticatedUser principal,
                          @PathVariable Long id,
                          @Valid @RequestBody RagSendRequest request,
                          HttpServletResponse response) {
        SseWriter writer = new SseWriter(response, mapper);
        try {
            chatService.streamRag(principal.id(), id, request.message(), request.documentIds(), writer);
        } catch (ApiException e) {
            if (!writer.started) {
                throw e;
            }
            writer.error(e);
        }
    }

    private static final class SseWriter implements AiChatService.StreamSink {
        private final HttpServletResponse response;
        private final ObjectMapper mapper;
        private boolean started;

        SseWriter(HttpServletResponse response, ObjectMapper mapper) {
            this.response = response;
            this.mapper = mapper;
        }

        @Override
        public void start(MessageResponse userMessage) {
            response.setStatus(HttpStatus.OK.value());
            response.setContentType("text/event-stream");
            response.setCharacterEncoding(StandardCharsets.UTF_8.name());
            response.setHeader("Cache-Control", "no-cache");
            response.setHeader("X-Accel-Buffering", "no");
            started = true;
            ObjectNode n = mapper.createObjectNode();
            n.set("userMessage", mapper.valueToTree(userMessage));
            write("start", n);
        }

        @Override
        public void delta(String text) {
            ObjectNode n = mapper.createObjectNode();
            n.put("text", text);
            write("delta", n);
        }

        @Override
        public void done(SendMessageResponse result) {
            write("done", mapper.valueToTree(result));
        }

        void error(ApiException e) {
            ObjectNode n = mapper.createObjectNode();
            n.put("code", e.getCode());
            n.put("message", e.getMessage());
            if (e.getRetryAfter() != null) {
                n.put("retryAfterSeconds", e.getRetryAfter());
            }
            try {
                write("error", n);
            } catch (StreamAbortedException ignored) {
            }
        }

        private void write(String event, Object json) {
            try {
                OutputStream out = response.getOutputStream();
                out.write(("event: " + event + "\ndata: " + mapper.writeValueAsString(json) + "\n\n").getBytes(StandardCharsets.UTF_8));
                out.flush();
                response.flushBuffer();
            } catch (IOException e) {
                throw new StreamAbortedException();
            }
        }
    }
}
