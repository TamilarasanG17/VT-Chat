package com.vtchat.repository;

import com.vtchat.entity.MessageReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface MessageReactionRepository extends JpaRepository<MessageReaction, Long> {

    @Query("select r from MessageReaction r where r.message.id in :messageIds order by r.createdAt asc, r.id asc")
    List<MessageReaction> findByMessageIds(@Param("messageIds") Collection<Long> messageIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = """
            insert into message_reactions (message_id, user_id, reaction, created_at)
            values (:messageId, :userId, :reaction, utc_timestamp(6))
            on duplicate key update reaction = values(reaction)
            """, nativeQuery = true)
    void upsert(@Param("messageId") Long messageId, @Param("userId") Long userId, @Param("reaction") String reaction);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from MessageReaction r where r.message.id = :messageId and r.user.id = :userId")
    void deleteByMessageAndUser(@Param("messageId") Long messageId, @Param("userId") Long userId);
}
