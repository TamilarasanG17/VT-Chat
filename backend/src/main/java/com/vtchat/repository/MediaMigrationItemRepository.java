package com.vtchat.repository;

import com.vtchat.entity.MediaMigrationItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MediaMigrationItemRepository extends JpaRepository<MediaMigrationItem, Long> {
    Optional<MediaMigrationItem> findByLegacyKey(String legacyKey);
}
