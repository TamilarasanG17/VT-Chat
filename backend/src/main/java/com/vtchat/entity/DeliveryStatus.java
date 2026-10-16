package com.vtchat.entity;

public enum DeliveryStatus {
    SENT, DELIVERED, READ;

    public boolean isAtLeast(DeliveryStatus other) {
        return ordinal() >= other.ordinal();
    }
}
