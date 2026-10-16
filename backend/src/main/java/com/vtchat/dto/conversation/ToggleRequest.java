package com.vtchat.dto.conversation;

public record ToggleRequest(Boolean enabled) {
    public boolean value() {
        return enabled == null || enabled;
    }
}
