package com.vtchat.repository;

import com.vtchat.entity.Conversation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface ConversationRepository extends JpaRepository<Conversation, Long> {

    Optional<Conversation> findByUserOneIdAndUserTwoId(Long userOneId, Long userTwoId);

    @Query("select c from Conversation c join fetch c.userOne join fetch c.userTwo where c.id = :id")
    Optional<Conversation> findWithUsersById(@Param("id") Long id);

    @Query("""
            select c from Conversation c
              join fetch c.userOne
              join fetch c.userTwo
            where c.userOne.id = :userId or c.userTwo.id = :userId
            order by c.updatedAt desc, c.id desc
            """)
    List<Conversation> findAllForUser(@Param("userId") Long userId);

    @Query("""
            select case when c.userOne.id = :userId then c.userTwo.id else c.userOne.id end
            from Conversation c
            where c.userOne.id = :userId or c.userTwo.id = :userId
            """)
    List<Long> findPeerIds(@Param("userId") Long userId);
}
