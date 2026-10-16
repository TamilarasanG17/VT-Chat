package com.vtchat.dto.message;

public record ReceiptEvent(String type, Long conversationId, Long userId, Long upToMessageId) {

    public static final String DELIVERED = "MESSAGE_DELIVERED";
    public static final String READ = "MESSAGE_READ";

    public static ReceiptEvent delivered(Long conversationId, Long receiverId, Long upToMessageId) {
        return new ReceiptEvent(DELIVERED, conversationId, receiverId, upToMessageId);
    }

    public static ReceiptEvent read(Long conversationId, Long readerId, Long upToMessageId) {
        return new ReceiptEvent(READ, conversationId, readerId, upToMessageId);
    }
}
