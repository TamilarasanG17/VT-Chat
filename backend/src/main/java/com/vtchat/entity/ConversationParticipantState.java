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
@Table(name = "conversation_participant_state",
        uniqueConstraints = @UniqueConstraint(name = "uk_participant_state_conversation_user", columnNames = {"conversation_id", "user_id"}),
        indexes = {
                @Index(name = "idx_participant_state_user_unread", columnList = "user_id, unread_count"),
                @Index(name = "idx_participant_state_user_pinned", columnList = "user_id, pinned"),
                @Index(name = "idx_participant_state_user_archived", columnList = "user_id, archived")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConversationParticipantState {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, foreignKey = @ForeignKey(name = "fk_participant_state_conversation"))
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_participant_state_user"))
    private User user;

    @Column(name = "last_read_message_id")
    private Long lastReadMessageId;

    @Builder.Default @ColumnDefault("0") @Column(name = "unread_count", nullable = false)
    private int unreadCount = 0;

    @Builder.Default @ColumnDefault("false") @Column(nullable = false)
    private boolean muted = false;

    @Builder.Default @ColumnDefault("false") @Column(nullable = false)
    private boolean pinned = false;

    @Builder.Default @ColumnDefault("false") @Column(nullable = false)
    private boolean archived = false;

    @Builder.Default @ColumnDefault("false") @Column(nullable = false)
    private boolean deleted = false;

    @Column(name = "cleared_at")
    private Instant clearedAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
