package com.vtchat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Entity
@Table(name = "messages",
        indexes = {
                @Index(name = "idx_messages_conversation_created", columnList = "conversation_id, created_at"),
                @Index(name = "idx_messages_sender", columnList = "sender_id"),
                @Index(name = "idx_messages_created", columnList = "created_at"),
                @Index(name = "idx_messages_reply_to", columnList = "reply_to_message_id"),
                @Index(name = "idx_messages_media_key", columnList = "media_key"),
                @Index(name = "idx_messages_conv_status", columnList = "conversation_id, delivery_status")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false, foreignKey = @ForeignKey(name = "fk_messages_conversation"))
    private Conversation conversation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false, foreignKey = @ForeignKey(name = "fk_messages_sender"))
    private User sender;

    @Column(nullable = false, length = 2000)
    private String content;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reply_to_message_id", foreignKey = @ForeignKey(name = "fk_messages_reply_to"))
    private Message replyToMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;

    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean edited;

    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean deleted;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean forwarded;

    @Column(name = "forwarded_from_message_id")
    private Long forwardedFromMessageId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @ColumnDefault("'TEXT'")
    @Column(name = "message_type", nullable = false, length = 16)
    private MessageType messageType = MessageType.TEXT;

    @Column(name = "media_key")
    private String mediaKey;

    @Column(name = "media_name")
    private String mediaName;

    @Column(name = "media_size")
    private Long mediaSize;

    @Column(name = "mime_type", length = 127)
    private String mimeType;

    @Column(name = "duration")
    private Integer duration;

    @Column(name = "call_id", length = 36)
    private String callId;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @ColumnDefault("'READ'")
    @Column(name = "delivery_status", nullable = false, length = 12)
    private DeliveryStatus deliveryStatus = DeliveryStatus.SENT;

    @Column(name = "call_status", length = 16)
    private String callStatus;
}
