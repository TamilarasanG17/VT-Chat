package com.vtchat.storage;

public class StorageException extends RuntimeException {

    public enum Kind {
        NOT_FOUND,
        ACCESS_DENIED,
        TIMEOUT,
        UNAVAILABLE,
        FAILED
    }

    private final Kind kind;

    public StorageException(Kind kind, String operation) {
        super("Object storage " + operation + " failed (" + kind + ")");
        this.kind = kind;
    }

    public Kind kind() {
        return kind;
    }
}
