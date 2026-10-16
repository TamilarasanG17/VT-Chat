package com.vtchat.ai;

public class AiServiceException extends RuntimeException {

    public enum Kind { UNAVAILABLE, TIMEOUT, BUSY, CONTEXT_TOO_LARGE, INVALID, RAG_UNAVAILABLE, DOCUMENT_REJECTED }

    private final Kind kind;
    private final int retryAfterSeconds;
    private final String code;

    public AiServiceException(Kind kind, String internalDetail) {
        this(kind, internalDetail, 0, "");
    }

    public AiServiceException(Kind kind, String internalDetail, int retryAfterSeconds) {
        this(kind, internalDetail, retryAfterSeconds, "");
    }

    public AiServiceException(Kind kind, String internalDetail, int retryAfterSeconds, String code) {
        super(internalDetail);
        this.kind = kind;
        this.retryAfterSeconds = retryAfterSeconds;
        this.code = code == null ? "" : code;
    }

    public Kind kind() {
        return kind;
    }

    public int retryAfterSeconds() {
        return retryAfterSeconds;
    }

    public String code() {
        return code;
    }
}
