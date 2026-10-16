package com.vtchat.dto.message;

public record MessageMediaResponse(Long messageId, String url, String thumbnailUrl, String downloadUrl, String viewUrl, long expiresIn) {
}
