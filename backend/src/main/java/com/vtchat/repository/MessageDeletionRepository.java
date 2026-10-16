package com.vtchat.repository;

import com.vtchat.entity.MessageDeletion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MessageDeletionRepository extends JpaRepository<MessageDeletion, Long> {

    @Query("select d.message.id from MessageDeletion d where d.user.id = :userId and d.message.id in :messageIds")
    List<Long> findHiddenIds(@Param("userId") Long userId, @Param("messageIds") Collection<Long> messageIds);

    @Query("select count(d) > 0 from MessageDeletion d where d.message.id = :messageId and d.user.id = :userId")
    boolean isHidden(@Param("messageId") Long messageId, @Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert ignore into message_deletions (message_id, user_id, deleted_at)
            select m.id, :userId, utc_timestamp(6) from messages m where m.conversation_id = :conversationId
            """, nativeQuery = true)
    int hideAllInConversation(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert ignore into message_deletions (message_id, user_id, deleted_at)
            values (:messageId, :userId, utc_timestamp(6))
            """, nativeQuery = true)
    void hide(@Param("messageId") Long messageId, @Param("userId") Long userId);
}
