package com.vtchat.repository;

import com.vtchat.entity.NotificationSettings;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface NotificationSettingsRepository extends JpaRepository<NotificationSettings, Long> {

    Optional<NotificationSettings> findByUserId(Long userId);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query(value = """
            insert ignore into notification_settings
              (user_id, message_notifications, missed_call_notifications, incoming_call_notifications,
               message_preview, browser_notifications, updated_at)
            values (:userId, 1, 1, 1, 1, 1, utc_timestamp(6))
            """, nativeQuery = true)
    void createDefaults(@org.springframework.data.repository.query.Param("userId") Long userId);
}
