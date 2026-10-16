package com.vtchat.entity;

public enum OtpPurpose {
    REGISTER,
    LOGIN,
    RESET_PASSWORD,
    RESET_GRANT;

    public boolean isUserFacing() {
        return this != RESET_GRANT;
    }
}
