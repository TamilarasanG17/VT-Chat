package com.vtchat.service;

import com.vtchat.dto.notification.NotificationSettingsRequest;
import com.vtchat.dto.notification.NotificationSettingsResponse;
import com.vtchat.entity.NotificationSettings;
import com.vtchat.repository.NotificationSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class NotificationSettingsService {

    private final NotificationSettingsRepository settings;

    @Transactional
    public NotificationSettings get(Long userId) {
        return settings.findByUserId(userId).orElseGet(() -> {
            settings.createDefaults(userId);
            return settings.findByUserId(userId).orElseThrow();
        });
    }

    @Transactional
    public NotificationSettingsResponse view(Long userId) {
        return NotificationSettingsResponse.of(get(userId));
    }

    @Transactional
    public NotificationSettingsResponse update(Long userId, NotificationSettingsRequest r) {
        NotificationSettings s = get(userId);
        if (r != null) {
            if (r.messageNotifications() != null) s.setMessageNotifications(r.messageNotifications());
            if (r.missedCallNotifications() != null) s.setMissedCallNotifications(r.missedCallNotifications());
            if (r.incomingCallNotifications() != null) s.setIncomingCallNotifications(r.incomingCallNotifications());
            if (r.messagePreview() != null) s.setMessagePreview(r.messagePreview());
            if (r.browserNotifications() != null) s.setBrowserNotifications(r.browserNotifications());
        }
        return NotificationSettingsResponse.of(settings.saveAndFlush(s));
    }
}
