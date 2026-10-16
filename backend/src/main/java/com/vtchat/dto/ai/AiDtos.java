package com.vtchat.dto.ai;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.List;

public final class AiDtos {

    private AiDtos() {
    }

    public record CreateConversationRequest(@Size(max = 120, message = "Title is too long") String title) {
    }

    public record ChatRequest(@NotBlank(message = "Message must not be empty") @Size(max = 100000, message = "Message is too long") String message) {
    }

    public record ChatResponse(String response, String model) {
    }

    public record SendMessageRequest(@NotBlank(message = "Message must not be empty") @Size(max = 100000, message = "Message is too long") String message) {
    }

    public record ConversationResponse(Long id, String title, Instant createdAt, Instant updatedAt) {
    }

    public record MessageResponse(Long id, String role, String content, Instant createdAt, String mode, List<SourceResponse> sources) {
    }

    public record SourceResponse(Long documentId, String fileName, Integer pageNumber) {
    }

    public record RagSendRequest(@NotBlank(message = "Message must not be empty") @Size(max = 100000, message = "Message is too long") String message,
                                 @Size(max = 500, message = "Too many documents selected") List<Long> documentIds) {
    }

    public record SendMessageResponse(MessageResponse userMessage, MessageResponse assistantMessage, ConversationResponse conversation, String model) {
    }
}
