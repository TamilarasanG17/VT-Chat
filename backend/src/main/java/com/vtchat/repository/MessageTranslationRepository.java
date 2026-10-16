package com.vtchat.repository;

import com.vtchat.entity.MessageTranslation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MessageTranslationRepository extends JpaRepository<MessageTranslation, Long> {

    Optional<MessageTranslation> findByMessageIdAndTargetLanguage(Long messageId, String targetLanguage);

    @Modifying(flushAutomatically = true)
    @Query("delete from MessageTranslation t where t.message.id = :messageId")
    int deleteByMessageId(@Param("messageId") Long messageId);
}
