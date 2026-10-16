package com.vtchat.repository;

import com.vtchat.entity.MediaDownload;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface MediaDownloadRepository extends JpaRepository<MediaDownload, Long> {

    @Query("select d.message.id from MediaDownload d where d.user.id = :userId and d.message.id in :messageIds")
    List<Long> findDownloadedIds(@Param("userId") Long userId, @Param("messageIds") Collection<Long> messageIds);

    @Transactional
    @Modifying
    @Query("update MediaDownload d set d.downloadedAt = :at where d.message.id = :messageId and d.user.id = :userId")
    int touch(@Param("messageId") Long messageId, @Param("userId") Long userId, @Param("at") Instant at);
}
