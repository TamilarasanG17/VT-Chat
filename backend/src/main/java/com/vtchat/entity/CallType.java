package com.vtchat.entity;

public enum CallType {
    VOICE, VIDEO;

    public MessageType recordType() {
        return this == VOICE ? MessageType.VOICE_CALL : MessageType.VIDEO_CALL;
    }
}
