package com.vtchat.dto.notification;

import java.util.List;

public record NotificationPageResponse(List<NotificationResponse> items, int page, int size, long totalElements,
                                       boolean hasMore, long unreadCount) {
}
