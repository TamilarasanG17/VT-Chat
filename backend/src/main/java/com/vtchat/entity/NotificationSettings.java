package com.vtchat.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "notification_settings",
        uniqueConstraints = @UniqueConstraint(name = "uk_notification_settings_user", columnNames = "user_id"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationSettings {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_notification_settings_user"))
    private User user;

    @Builder.Default @ColumnDefault("true") @Column(name = "message_notifications", nullable = false)
    private boolean messageNotifications = true;

    @Builder.Default @ColumnDefault("true") @Column(name = "missed_call_notifications", nullable = false)
    private boolean missedCallNotifications = true;

    @Builder.Default @ColumnDefault("true") @Column(name = "incoming_call_notifications", nullable = false)
    private boolean incomingCallNotifications = true;

    @Builder.Default @ColumnDefault("true") @Column(name = "message_preview", nullable = false)
    private boolean messagePreview = true;

    @Builder.Default @ColumnDefault("true") @Column(name = "browser_notifications", nullable = false)
    private boolean browserNotifications = true;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
