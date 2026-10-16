package com.vtchat.translation;

/**
 * A provider call failed. Carries only a category, never the provider's response, URL or key,
 * so it is always safe to log and can never leak credentials.
 */
public class TranslationProviderException extends RuntimeException {

    public enum Kind { NOT_CONFIGURED, INVALID_KEY, RATE_LIMITED, TIMEOUT, UNAVAILABLE, REJECTED, BAD_RESPONSE }

    private final Kind kind;

    public TranslationProviderException(Kind kind) {
        super(kind.name());
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }
}
