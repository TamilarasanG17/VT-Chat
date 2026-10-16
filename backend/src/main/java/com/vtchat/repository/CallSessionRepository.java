package com.vtchat.repository;

import com.vtchat.entity.CallSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface CallSessionRepository extends JpaRepository<CallSession, Long> {

    boolean existsByCallId(String callId);

    Optional<CallSession> findByCallId(String callId);

    @Query("""
            select c from CallSession c join fetch c.caller join fetch c.receiver join fetch c.conversation
            where c.callId = :callId
            """)
    Optional<CallSession> findByCallIdWithUsers(@Param("callId") String callId);

    @Query(value = """
            select c from CallSession c join fetch c.caller join fetch c.receiver
            where c.conversation.id = :conversationId
            order by c.createdAt desc, c.id desc
            """,
            countQuery = "select count(c) from CallSession c where c.conversation.id = :conversationId")
    Page<CallSession> findForConversation(@Param("conversationId") Long conversationId, Pageable pageable);

    @Query(value = """
            select c from CallSession c join fetch c.caller join fetch c.receiver
            where c.caller.id = :userId or c.receiver.id = :userId
            order by c.createdAt desc, c.id desc
            """,
            countQuery = "select count(c) from CallSession c where c.caller.id = :userId or c.receiver.id = :userId")
    Page<CallSession> findForUser(@Param("userId") Long userId, Pageable pageable);

    @Modifying
    @Query("""
            update CallSession c set c.status = com.vtchat.entity.CallStatus.FAILED, c.endedAt = :now, c.duration = 0
            where c.status in (com.vtchat.entity.CallStatus.RINGING, com.vtchat.entity.CallStatus.CONNECTED)
            """)
    int failAllActive(@Param("now") Instant now);

    @Query("""
            select c.callId from CallSession c
            where c.status in (com.vtchat.entity.CallStatus.RINGING, com.vtchat.entity.CallStatus.CONNECTED)
            """)
    List<String> findActiveCallIds();

    @Modifying
    @Query("""
            update CallSession c set c.status = com.vtchat.entity.CallStatus.FAILED, c.endedAt = :now, c.duration = 0
            where c.callId = :callId
              and c.status in (com.vtchat.entity.CallStatus.RINGING, com.vtchat.entity.CallStatus.CONNECTED)
            """)
    int failByCallId(@Param("callId") String callId, @Param("now") Instant now);
}
