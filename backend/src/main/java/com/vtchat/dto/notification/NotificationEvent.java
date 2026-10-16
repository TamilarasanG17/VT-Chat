package com.vtchat.dto.notification;

public record NotificationEvent(String type, NotificationResponse notification, long unreadCount, Long conversationId) {

    public static final String CREATED = "NOTIFICATION_CREATED";
    public static final String UPDATED = "NOTIFICATIONS_UPDATED";

    public static NotificationEvent created(NotificationResponse n, long unread) {
        return new NotificationEvent(CREATED, n, unread, n.conversationId());
    }

    public static NotificationEvent updated(long unread, Long conversationId) {
        return new NotificationEvent(UPDATED, null, unread, conversationId);
    }
}
