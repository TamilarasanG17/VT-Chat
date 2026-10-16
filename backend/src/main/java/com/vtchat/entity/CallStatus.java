package com.vtchat.entity;

public enum CallStatus {
    RINGING, CONNECTED, COMPLETED, MISSED, REJECTED, CANCELLED, FAILED, ENDED;

    public boolean isActive() {
        return this == RINGING || this == CONNECTED;
    }
}
