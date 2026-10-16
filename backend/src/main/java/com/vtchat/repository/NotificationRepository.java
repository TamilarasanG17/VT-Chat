package com.vtchat.repository;

import com.vtchat.entity.Notification;
import com.vtchat.entity.NotificationType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    @Query(value = """
            select n from Notification n
              left join fetch n.callReference
            where n.recipient.id = :userId
            order by n.createdAt desc, n.id desc
            """,
            countQuery = "select count(n) from Notification n where n.recipient.id = :userId")
    Page<Notification> findPage(@Param("userId") Long userId, Pageable pageable);

    @Query("select n from Notification n left join fetch n.callReference where n.id = :id and n.recipient.id = :userId")
    Optional<Notification> findOwned(@Param("id") Long id, @Param("userId") Long userId);

    @Query("select count(n) from Notification n where n.recipient.id = :userId and n.read = false")
    long countUnread(@Param("userId") Long userId);

    boolean existsByRecipientIdAndTypeAndMessageReferenceId(Long recipientId, NotificationType type, Long messageId);

    boolean existsByRecipientIdAndTypeAndCallReferenceId(Long recipientId, NotificationType type, Long callSessionId);

    @Modifying(flushAutomatically = true)
    @Query("update Notification n set n.read = true where n.id = :id and n.recipient.id = :userId and n.read = false")
    int markRead(@Param("id") Long id, @Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query("update Notification n set n.read = true where n.recipient.id = :userId and n.read = false")
    int markAllRead(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query("update Notification n set n.read = true where n.recipient.id = :userId and n.conversation.id = :conversationId and n.read = false")
    int markConversationRead(@Param("userId") Long userId, @Param("conversationId") Long conversationId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update Notification n set n.read = true
            where n.recipient.id = :userId and n.type = :type and n.callReference.id = :callSessionId and n.read = false
            """)
    int markCallRead(@Param("userId") Long userId, @Param("type") NotificationType type, @Param("callSessionId") Long callSessionId);

    @Modifying(flushAutomatically = true)
    boolean existsByRecipientIdAndTypeAndChatRequestId(Long recipientId, NotificationType type, Long chatRequestId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Notification n set n.read = true where n.recipient.id = :userId and n.type = com.vtchat.entity.NotificationType.CHAT_REQUEST and n.chatRequestId = :requestId and n.read = false")
    int markChatRequestRead(@Param("userId") Long userId, @Param("requestId") Long requestId);

    @Query("delete from Notification n where n.id = :id and n.recipient.id = :userId")
    int deleteOwned(@Param("id") Long id, @Param("userId") Long userId);
}
