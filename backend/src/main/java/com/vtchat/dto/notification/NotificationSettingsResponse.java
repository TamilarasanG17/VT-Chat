package com.vtchat.dto.notification;

import com.vtchat.entity.NotificationSettings;

public record NotificationSettingsResponse(boolean messageNotifications, boolean missedCallNotifications,
                                           boolean incomingCallNotifications, boolean messagePreview,
                                           boolean browserNotifications) {
    public static NotificationSettingsResponse of(NotificationSettings s) {
        return new NotificationSettingsResponse(s.isMessageNotifications(), s.isMissedCallNotifications(),
                s.isIncomingCallNotifications(), s.isMessagePreview(), s.isBrowserNotifications());
    }
}
