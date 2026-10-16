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
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "chat_requests",
        uniqueConstraints = @UniqueConstraint(name = "uk_chat_requests_pending_pair", columnNames = "pending_pair_key"),
        indexes = {
                @Index(name = "idx_chat_requests_receiver_status", columnList = "receiver_id, status, created_at"),
                @Index(name = "idx_chat_requests_sender_created", columnList = "sender_id, created_at"),
                @Index(name = "idx_chat_requests_pair", columnList = "pair_low_id, pair_high_id, id")
        })
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "sender_id", nullable = false, foreignKey = @ForeignKey(name = "fk_chat_requests_sender"))
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiver_id", nullable = false, foreignKey = @ForeignKey(name = "fk_chat_requests_receiver"))
    private User receiver;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ChatRequestStatus status;

    @Column(name = "pair_low_id", nullable = false)
    private Long pairLowId;

    @Column(name = "pair_high_id", nullable = false)
    private Long pairHighId;

    @Column(name = "pending_pair_key", length = 40)
    private String pendingPairKey;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "responded_at")
    private Instant respondedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "ended_by_id")
    private Long endedById;

    public static String pairKey(Long a, Long b) {
        return Math.min(a, b) + ":" + Math.max(a, b);
    }
}
