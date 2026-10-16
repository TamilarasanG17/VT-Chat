package com.vtchat.repository;

import com.vtchat.entity.ConversationParticipantState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationParticipantStateRepository extends JpaRepository<ConversationParticipantState, Long> {

    Optional<ConversationParticipantState> findByConversationIdAndUserId(Long conversationId, Long userId);

    @Query("select s from ConversationParticipantState s where s.user.id = :userId")
    List<ConversationParticipantState> findAllForUser(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
            insert ignore into conversation_participant_state
              (conversation_id, user_id, unread_count, muted, pinned, archived, deleted, updated_at)
            values (:conversationId, :userId, 0, 0, 0, 0, 0, utc_timestamp(6))
            """, nativeQuery = true)
    void ensure(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update ConversationParticipantState s set s.unreadCount = s.unreadCount + 1, s.deleted = false
            where s.conversation.id = :conversationId and s.user.id = :userId
            """)
    int incrementUnread(@Param("conversationId") Long conversationId, @Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query("""
            update ConversationParticipantState s set s.deleted = false
            where s.conversation.id = :conversationId and s.user.id = :userId
            """)
    int resurface(@Param("conversationId") Long conversationId, @Param("userId") Long userId);
}
