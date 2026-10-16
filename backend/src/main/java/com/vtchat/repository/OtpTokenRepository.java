package com.vtchat.repository;

import com.vtchat.entity.OtpPurpose;
import com.vtchat.entity.OtpToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;

public interface OtpTokenRepository extends JpaRepository<OtpToken, Long> {

    Optional<OtpToken> findFirstByEmailAndPurposeOrderByCreatedAtDescIdDesc(String email, OtpPurpose purpose);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<OtpToken> findFirstByEmailAndPurposeAndUsedFalseOrderByCreatedAtDescIdDesc(String email, OtpPurpose purpose);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OtpToken o set o.used = true where o.email = :email and o.purpose = :purpose and o.used = false")
    int invalidateActive(@Param("email") String email, @Param("purpose") OtpPurpose purpose);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update OtpToken o set o.used = true where o.email = :email and o.used = false")
    int invalidateAllForEmail(@Param("email") String email);

    @Modifying
    @Query("delete from OtpToken o where o.createdAt < :before")
    int deleteCreatedBefore(@Param("before") Instant before);
}
