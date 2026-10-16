package com.vtchat.repository;

import com.vtchat.entity.KnowledgeDocument;
import com.vtchat.entity.KnowledgeDocumentStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface KnowledgeDocumentRepository extends JpaRepository<KnowledgeDocument, Long> {

        @Query("select d from KnowledgeDocument d join fetch d.user where d.id = :id")
        Optional<KnowledgeDocument> findWithUser(@Param("id") Long id);

        @Query("select d from KnowledgeDocument d where d.id = :id and d.user.id = :userId and d.status <> com.vtchat.entity.KnowledgeDocumentStatus.DELETED")
        Optional<KnowledgeDocument> findOwned(@Param("id") Long id, @Param("userId") Long userId);

        @Query("select d from KnowledgeDocument d where d.user.id = :userId and d.status <> com.vtchat.entity.KnowledgeDocumentStatus.DELETED order by d.createdAt desc, d.id desc")
        List<KnowledgeDocument> findAllOwned(@Param("userId") Long userId);

        @Query("select count(d) from KnowledgeDocument d where d.user.id = :userId and d.status <> com.vtchat.entity.KnowledgeDocumentStatus.DELETED")
        long countOwned(@Param("userId") Long userId);

        @Query("select count(d) > 0 from KnowledgeDocument d where d.user.id = :userId and d.contentHash = :hash and d.status <> com.vtchat.entity.KnowledgeDocumentStatus.DELETED")
        boolean existsDuplicate(@Param("userId") Long userId, @Param("hash") String hash);

        @Query("""
                        select d from KnowledgeDocument d
                        where d.user.id = :userId and d.status = com.vtchat.entity.KnowledgeDocumentStatus.READY
                          and (:filter = false or d.id in :ids)
                        order by d.id desc
                        """)
        List<KnowledgeDocument> findReady(@Param("userId") Long userId, @Param("filter") boolean filter,
                        @Param("ids") Collection<Long> ids, Pageable pageable);

        @Modifying
        @Transactional
        @Query("""
                        update KnowledgeDocument d set d.status = com.vtchat.entity.KnowledgeDocumentStatus.PROCESSING,
                        d.processingVersion = d.processingVersion + 1, d.errorMessage = null, d.updatedAt = :now
                        where d.id = :id and d.status in :from
                        """)
        int claim(@Param("id") Long id, @Param("from") Collection<KnowledgeDocumentStatus> from,
                        @Param("now") Instant now);

        @Modifying
        @Transactional
        @Query("""
                        update KnowledgeDocument d set d.status = :status, d.chunkCount = :chunks, d.pageCount = :pages,
                               d.errorMessage = :error, d.updatedAt = :now
                        where d.id = :id and d.processingVersion = :version
                          and d.status = com.vtchat.entity.KnowledgeDocumentStatus.PROCESSING
                        """)
        int finish(@Param("id") Long id, @Param("version") int version, @Param("status") KnowledgeDocumentStatus status,
                        @Param("chunks") int chunks, @Param("pages") Integer pages, @Param("error") String error,
                        @Param("now") Instant now);

        @Modifying
        @Transactional
        @Query("""
                        update KnowledgeDocument d set d.status = com.vtchat.entity.KnowledgeDocumentStatus.DELETED,
                               d.processingVersion = d.processingVersion + 1, d.updatedAt = :now
                        where d.id = :id and d.user.id = :userId and d.status <> com.vtchat.entity.KnowledgeDocumentStatus.DELETED
                        """)
        int markDeleted(@Param("id") Long id, @Param("userId") Long userId, @Param("now") Instant now);

        @Query("select d from KnowledgeDocument d where d.status = :status and d.updatedAt < :before order by d.updatedAt")
        List<KnowledgeDocument> findStale(@Param("status") KnowledgeDocumentStatus status,
                        @Param("before") Instant before, Pageable pageable);

        @Modifying
        @Transactional
        @Query("""
                        update KnowledgeDocument d set d.status = com.vtchat.entity.KnowledgeDocumentStatus.FAILED,
                               d.processingVersion = d.processingVersion + 1, d.errorMessage = :error, d.updatedAt = :now
                        where d.id = :id and d.status = com.vtchat.entity.KnowledgeDocumentStatus.PROCESSING and d.updatedAt < :before
                        """)
        int failStuck(@Param("id") Long id, @Param("before") Instant before, @Param("error") String error,
                        @Param("now") Instant now);
}
