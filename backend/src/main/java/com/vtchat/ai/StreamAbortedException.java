package com.vtchat.ai;

public class StreamAbortedException extends RuntimeException {
    public StreamAbortedException() {
        super("stream aborted by the client", null, false, false);
    }
}
