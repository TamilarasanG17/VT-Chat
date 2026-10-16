package com.vtchat.repository;

import com.vtchat.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface MessageRepository extends JpaRepository<Message, Long> {

    @Query("""
            select m from Message m left join fetch m.replyToMessage
            where m.conversation.id = :conversationId
              and not exists (select 1 from MessageDeletion d where d.message = m and d.user.id = :userId)
            order by m.createdAt desc, m.id desc
            """)
    List<Message> findRecentVisible(@Param("conversationId") Long conversationId,
                                    @Param("userId") Long userId,
                                    Pageable pageable);

    @Query("""
            select m from Message m left join fetch m.replyToMessage
            where m.conversation.id = :conversationId and m.id <= :messageId
              and not exists (select 1 from MessageDeletion d where d.message = m and d.user.id = :userId)
            order by m.id desc
            """)
    List<Message> findVisibleUpTo(@Param("conversationId") Long conversationId,
                                  @Param("userId") Long userId,
                                  @Param("messageId") Long messageId,
                                  Pageable pageable);

    @Query("""
            select m from Message m left join fetch m.replyToMessage
            where m.conversation.id = :conversationId and m.id > :messageId
              and not exists (select 1 from MessageDeletion d where d.message = m and d.user.id = :userId)
            order by m.id asc
            """)
    List<Message> findVisibleAfter(@Param("conversationId") Long conversationId,
                                   @Param("userId") Long userId,
                                   @Param("messageId") Long messageId,
                                   Pageable pageable);

    @Query("""
            select m from Message m join fetch m.sender
            where m.conversation.id = :conversationId
              and m.deleted = false
              and (lower(m.content) like :pattern escape '!' or lower(m.mediaName) like :pattern escape '!')
              and not exists (select 1 from MessageDeletion d where d.message = m and d.user.id = :userId)
            order by m.createdAt desc, m.id desc
            """)
    List<Message> searchVisible(@Param("conversationId") Long conversationId,
                                @Param("userId") Long userId,
                                @Param("pattern") String pattern,
                                Pageable pageable);

    @Query("""
            select m.mediaKey, min(m.conversation.id), min(m.mimeType) from Message m
            where m.mediaKey is not null and m.mediaKey not like 'cld:%'
            group by m.mediaKey order by min(m.id)
            """)
    List<Object[]> findLegacyMediaKeys();

    @org.springframework.data.jpa.repository.Modifying
    @Query("update Message m set m.mediaKey = :newKey where m.mediaKey = :oldKey")
    int repointMediaKey(@Param("oldKey") String oldKey, @Param("newKey") String newKey);

    long countByMediaKeyAndDeletedFalse(String mediaKey);

    @Query("""
            select m from Message m
              join fetch m.conversation c
              join fetch c.userOne
              join fetch c.userTwo
              left join fetch m.replyToMessage
            where m.id = :id
            """)
    Optional<Message> findWithConversationById(@Param("id") Long id);

    @Query("""
            select m.id from Message m
            where m.conversation.id = :conversationId and m.sender.id <> :userId
              and m.id <= :upTo and m.deliveryStatus <> com.vtchat.entity.DeliveryStatus.READ
            """)
    List<Long> findUnreadIncomingIds(@Param("conversationId") Long conversationId, @Param("userId") Long userId,
                                     @Param("upTo") Long upTo);

    @org.springframework.data.jpa.repository.Modifying
    @Query("""
            update Message m set m.deliveryStatus = com.vtchat.entity.DeliveryStatus.READ
            where m.id in :ids and m.deliveryStatus <> com.vtchat.entity.DeliveryStatus.READ
            """)
    int markRead(@Param("ids") Collection<Long> ids);

    @org.springframework.data.jpa.repository.Modifying
    @Query(value = """
            insert ignore into message_read_receipts (message_id, user_id, read_at)
            select m.id, :userId, :at from messages m where m.id in (:ids)
            """, nativeQuery = true)
    int insertReceipts(@Param("ids") Collection<Long> ids, @Param("userId") Long userId, @Param("at") java.time.Instant at);

    @Query("""
            select m.conversation.id, max(m.id) from Message m
            where m.deliveryStatus = com.vtchat.entity.DeliveryStatus.SENT and m.sender.id <> :userId
              and (m.conversation.userOne.id = :userId or m.conversation.userTwo.id = :userId)
            group by m.conversation.id
            """)
    List<Object[]> findPendingDelivery(@Param("userId") Long userId);

    @org.springframework.data.jpa.repository.Modifying
    @Query("""
            update Message m set m.deliveryStatus = com.vtchat.entity.DeliveryStatus.DELIVERED
            where m.conversation.id = :conversationId and m.sender.id <> :userId and m.id <= :upTo
              and m.deliveryStatus = com.vtchat.entity.DeliveryStatus.SENT
            """)
    int markDelivered(@Param("conversationId") Long conversationId, @Param("userId") Long userId, @Param("upTo") Long upTo);

    @Query("select max(m.id) from Message m where m.conversation.id = :conversationId")
    Long findMaxId(@Param("conversationId") Long conversationId);

    @Query("""
            select count(m) from Message m
            where m.conversation.id = :conversationId and m.sender.id <> :userId and m.id > :after and m.deleted = false
              and (m.messageType not in (com.vtchat.entity.MessageType.VOICE_CALL, com.vtchat.entity.MessageType.VIDEO_CALL)
                   or m.callStatus = 'MISSED')
              and not exists (select 1 from MessageDeletion d where d.message = m and d.user.id = :userId)
            """)
    long countUnreadAfter(@Param("conversationId") Long conversationId, @Param("userId") Long userId, @Param("after") Long after);

    @Query("""
            select m from Message m
            where m.id in (
                select max(m2.id) from Message m2
                where m2.conversation.id in :conversationIds
                  and not exists (select 1 from MessageDeletion d where d.message = m2 and d.user.id = :userId)
                group by m2.conversation.id)
            """)
    List<Message> findLatestForConversations(@Param("conversationIds") Collection<Long> conversationIds,
                                             @Param("userId") Long userId);
}
