package com.vtchat.dto;

import java.util.Map;

public record ApiResponse<T>(boolean success, String message, T data) {

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data);
    }

    public static ApiResponse<Map<String, Object>> ok(String message) {
        return new ApiResponse<>(true, message, Map.of());
    }
}
