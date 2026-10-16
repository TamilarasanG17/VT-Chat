package com.vtchat.repository;

import com.vtchat.entity.UserPresence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

public interface UserPresenceRepository extends JpaRepository<UserPresence, Long> {

    @Transactional
    @Modifying
    @Query("update UserPresence p set p.lastSeenAt = :at, p.updatedAt = :at where p.userId = :userId")
    int touch(@Param("userId") Long userId, @Param("at") Instant at);
}
