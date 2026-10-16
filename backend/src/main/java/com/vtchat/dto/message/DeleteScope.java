package com.vtchat.dto.message;

import com.vtchat.exception.ApiException;
import org.springframework.http.HttpStatus;

public enum DeleteScope {
    ME, EVERYONE;

    public static DeleteScope parse(String raw) {
        if (raw != null) {
            for (DeleteScope scope : values()) {
                if (scope.name().equalsIgnoreCase(raw.trim())) {
                    return scope;
                }
            }
        }
        throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", "Choose Delete for me or Delete for everyone");
    }
}
