package com.vtchat.dto.ai;

import java.time.Instant;

public final class KnowledgeDtos {

    private KnowledgeDtos() {
    }

    public record DocumentResponse(Long id, String fileName, String fileType, long fileSize, String status, int chunkCount,
                                   Integer pageCount, String errorMessage, Instant createdAt, Instant updatedAt) {
    }

    public record AccessResponse(Long documentId, String viewUrl, String downloadUrl, long expiresIn) {
    }
}
