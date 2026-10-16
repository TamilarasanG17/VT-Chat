package com.vtchat.translation;

public class TranslationServiceException extends RuntimeException {

    public enum Kind { UNAVAILABLE, TIMEOUT, REJECTED, AUTH, BAD_RESPONSE }

    private final Kind kind;

    public TranslationServiceException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
