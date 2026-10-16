package com.vtchat.repository;

import com.vtchat.entity.AiConversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface AiConversationRepository extends JpaRepository<AiConversation, Long> {

    @Query("select c from AiConversation c where c.user.id = :userId order by c.updatedAt desc, c.id desc")
    List<AiConversation> findByUser(@Param("userId") Long userId, Pageable pageable);

    @Query("select c from AiConversation c where c.id = :id and c.user.id = :userId")
    Optional<AiConversation> findOwned(@Param("id") Long id, @Param("userId") Long userId);

    @Query("select count(c) from AiConversation c where c.user.id = :userId")
    long countByUser(@Param("userId") Long userId);

    @Modifying
    @Query("update AiConversation c set c.updatedAt = :now where c.id = :id")
    int touch(@Param("id") Long id, @Param("now") Instant now);

    @Modifying
    @Query("update AiConversation c set c.title = :title, c.updatedAt = :now where c.id = :id")
    int rename(@Param("id") Long id, @Param("title") String title, @Param("now") Instant now);

    @Modifying
    @Query("delete from AiConversation c where c.id = :id and c.user.id = :userId")
    int deleteOwned(@Param("id") Long id, @Param("userId") Long userId);
}
