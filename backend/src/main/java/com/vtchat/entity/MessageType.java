package com.vtchat.entity;

public enum MessageType {
    TEXT, IMAGE, VIDEO, FILE, VOICE, VOICE_CALL, VIDEO_CALL;

    public boolean isMedia() {
        return this == IMAGE || this == VIDEO || this == FILE || this == VOICE;
    }

    public boolean isCall() {
        return this == VOICE_CALL || this == VIDEO_CALL;
    }
}
