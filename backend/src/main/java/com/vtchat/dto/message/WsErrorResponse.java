package com.vtchat.dto.message;

public record WsErrorResponse(int status, String code, String message, Integer retryAfterSeconds) {

    public WsErrorResponse(int status, String code, String message) {
        this(status, code, message, null);
    }
}
