package com.vtchat.repository;

import com.vtchat.entity.AiMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AiMessageRepository extends JpaRepository<AiMessage, Long> {

    @Query("""
            select m from AiMessage m
            where m.conversation.id = :conversationId and (:before is null or m.id < :before)
            order by m.id desc
            """)
    List<AiMessage> findNewest(@Param("conversationId") Long conversationId, @Param("before") Long before, Pageable pageable);

    @Modifying
    @Query("delete from AiMessage m where m.conversation.id = :conversationId")
    int deleteByConversation(@Param("conversationId") Long conversationId);
}
