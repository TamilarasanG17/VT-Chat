package com.vtchat.repository;

import com.vtchat.entity.ChatRequest;
import com.vtchat.entity.ChatRequestStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ChatRequestRepository extends JpaRepository<ChatRequest, Long> {

    @Query("select r from ChatRequest r join fetch r.sender join fetch r.receiver where r.pendingPairKey = :key")
    Optional<ChatRequest> findPending(@Param("key") String key);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ChatRequest r join fetch r.sender join fetch r.receiver where r.id = :id")
    Optional<ChatRequest> lockById(@Param("id") Long id);

    @Query("select r from ChatRequest r join fetch r.sender join fetch r.receiver where r.id = :id")
    Optional<ChatRequest> findWithUsersById(@Param("id") Long id);

    @Query("""
            select r from ChatRequest r join fetch r.sender join fetch r.receiver
            where r.receiver.id = :userId and r.status = com.vtchat.entity.ChatRequestStatus.PENDING
            order by r.createdAt desc, r.id desc
            """)
    List<ChatRequest> findReceivedPending(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            select r from ChatRequest r join fetch r.sender join fetch r.receiver
            where r.sender.id = :userId
            order by r.createdAt desc, r.id desc
            """)
    List<ChatRequest> findSent(@Param("userId") Long userId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from ChatRequest r where r.pairLowId = :low and r.pairHighId = :high and r.status = com.vtchat.entity.ChatRequestStatus.ACCEPTED")
    List<ChatRequest> lockAccepted(@Param("low") Long low, @Param("high") Long high);

    @Query("""
            select r.pairLowId, r.pairHighId from ChatRequest r
            where r.pairLowId = :userId or r.pairHighId = :userId
            group by r.pairLowId, r.pairHighId
            having sum(case when r.status = com.vtchat.entity.ChatRequestStatus.ACCEPTED then 1 else 0 end) = 0
            """)
    List<Object[]> findBlockedPairs(@Param("userId") Long userId);

    long countByReceiverIdAndStatus(Long receiverId, ChatRequestStatus status);

    long countBySenderIdAndStatus(Long senderId, ChatRequestStatus status);

    Optional<ChatRequest> findFirstByPairLowIdAndPairHighIdOrderByIdDesc(Long low, Long high);

    Optional<ChatRequest> findFirstBySenderIdAndReceiverIdAndStatusOrderByIdDesc(Long senderId, Long receiverId, ChatRequestStatus status);

    boolean existsByPairLowIdAndPairHighId(Long low, Long high);

    boolean existsByPairLowIdAndPairHighIdAndStatus(Long low, Long high, ChatRequestStatus status);
}
