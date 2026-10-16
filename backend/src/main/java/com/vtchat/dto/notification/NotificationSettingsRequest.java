package com.vtchat.dto.notification;

public record NotificationSettingsRequest(Boolean messageNotifications, Boolean missedCallNotifications,
                                          Boolean incomingCallNotifications, Boolean messagePreview,
                                          Boolean browserNotifications) {
}
