package com.vtchat.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "media_migration_items",
        uniqueConstraints = @UniqueConstraint(name = "uk_media_migration_legacy", columnNames = "legacy_key"),
        indexes = @Index(name = "idx_media_migration_status", columnList = "status"))
@Getter
@Setter
@NoArgsConstructor
public class MediaMigrationItem {

    public enum Status { PENDING, DONE, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "legacy_key", nullable = false, length = 255)
    private String legacyKey;

    @Column(name = "new_key", nullable = false, length = 255)
    private String newKey;

    @Column(nullable = false, length = 10)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Status status = Status.PENDING;

    @Column(nullable = false)
    private int attempts;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "last_error", length = 255)
    private String lastError;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();
}
