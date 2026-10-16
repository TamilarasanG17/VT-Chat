package com.vtchat.repository;

import com.vtchat.entity.User;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    @Query("""
            select u from User u
            where u.id <> :currentUserId
              and u.emailVerified = true
              and (lower(u.username) like :pattern escape '!' or lower(u.email) like :pattern escape '!')
            order by u.username asc, u.id asc
            """)
    List<User> search(@Param("currentUserId") Long currentUserId,
                      @Param("pattern") String pattern,
                      Pageable pageable);

    @Query("select count(u) > 0 from User u where u.id <> :userId and u.emailVerified = true and lower(u.username) = :lower")
    boolean existsByUsernameTaken(@Param("userId") Long userId, @Param("lower") String lower);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.profilePicture = :next where u.id = :id and u.profilePicture is null")
    int setProfilePictureIfNone(@Param("id") Long id, @Param("next") String next);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.profilePicture = :next where u.id = :id and u.profilePicture = :prev")
    int replaceProfilePicture(@Param("id") Long id, @Param("prev") String prev, @Param("next") String next);

    @Transactional
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update User u set u.profilePicture = null where u.id = :id and u.profilePicture = :prev")
    int clearProfilePicture(@Param("id") Long id, @Param("prev") String prev);

    long countByProfilePicture(String profilePicture);

    @Query("select u.id, u.profilePicture from User u where u.profilePicture is not null and u.profilePicture not like 'cld:%' order by u.id")
    List<Object[]> findLegacyAvatars();
}
