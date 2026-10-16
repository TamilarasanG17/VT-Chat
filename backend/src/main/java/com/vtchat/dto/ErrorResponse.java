package com.vtchat.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.Map;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(boolean success,
                            String message,
                            String code,
                            Integer retryAfter,
                            Integer retryAfterSeconds,
                            Map<String, String> errors) {

    public static ErrorResponse of(String message, String code, Integer retryAfter, Map<String, String> errors) {
        return new ErrorResponse(false, message, code, retryAfter, retryAfter, errors);
    }
}
