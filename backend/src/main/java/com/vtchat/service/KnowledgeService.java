package com.vtchat.service;

import com.vtchat.config.RagProperties;
import com.vtchat.config.RequestIdFilter;
import com.vtchat.dto.ai.KnowledgeDtos.AccessResponse;
import com.vtchat.dto.ai.KnowledgeDtos.DocumentResponse;
import com.vtchat.entity.KnowledgeDocument;
import com.vtchat.entity.KnowledgeDocumentStatus;
import com.vtchat.exception.ApiException;
import com.vtchat.repository.KnowledgeDocumentRepository;
import com.vtchat.repository.UserRepository;
import com.vtchat.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeService {

    private final RagProperties props;
    private final KnowledgeDocumentRepository documents;
    private final UserRepository users;
    private final KnowledgeFileInspector inspector;
    private final MediaStorageService storage;
    private final KnowledgeProcessor processor;
    private final JwtService jwtService;

    public record Loaded(KnowledgeDocument document, Resource resource) {
    }

    public DocumentResponse upload(Long userId, MultipartFile file) {
        requireEnabled();
        KnowledgeFileInspector.Detected detected = inspector.inspect(file);
        if (documents.countOwned(userId) >= props.getMaxDocumentsPerUser()) {
            throw ApiException.conflict("KNOWLEDGE_LIMIT_REACHED",
                    "You have reached the limit of " + props.getMaxDocumentsPerUser() + " documents. Delete one to upload another.");
        }
        MessageDigest sha = sha256();
        String key;
        try (InputStream in = new DigestInputStream(file.getInputStream(), sha)) {
            key = storage.store(MediaStorageService.StoreRequest.knowledge(userId, detected.extension(), detected.mimeType(), file.getSize()), in);
        } catch (IOException | com.vtchat.storage.StorageException e) {
            log.warn("Could not store knowledge document userId={} error={}", userId, com.vtchat.storage.LogSanitizer.describe(e));
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "UPLOAD_FAILED", "The file could not be saved. Please try again.");
        }
        String hash = HexFormat.of().formatHex(sha.digest());
        KnowledgeDocument saved;
        try {
            if (documents.existsDuplicate(userId, hash)) {
                storage.delete(key);
                throw ApiException.conflict("DUPLICATE_DOCUMENT", "You have already uploaded this document.");
            }
            Instant now = Instant.now();
            saved = documents.save(KnowledgeDocument.builder()
                    .user(users.getReferenceById(userId))
                    .fileName(safeName(file.getOriginalFilename(), detected.extension()))
                    .fileType(detected.extension())
                    .fileSize(file.getSize())
                    .storagePath(key)
                    .contentHash(hash)
                    .status(KnowledgeDocumentStatus.UPLOADED)
                    .chunkCount(0)
                    .processingVersion(0)
                    .createdAt(now)
                    .updatedAt(now)
                    .build());
        } catch (ApiException e) {
            throw e;
        } catch (RuntimeException e) {
            storage.delete(key);
            throw e;
        }
        log.info("Knowledge document uploaded documentId={} userId={} type={} bytes={}", saved.getId(), userId, saved.getFileType(), saved.getFileSize());
        dispatch(saved.getId());
        return toResponse(saved);
    }

    public List<DocumentResponse> list(Long userId) {
        requireEnabled();
        return documents.findAllOwned(userId).stream().map(KnowledgeService::toResponse).toList();
    }

    public DocumentResponse get(Long userId, Long id) {
        return toResponse(owned(userId, id));
    }

    public void delete(Long userId, Long id) {
        owned(userId, id);
        if (documents.markDeleted(id, userId, Instant.now()) == 0) {
            throw notFound();
        }
        log.info("Knowledge document deleted documentId={} userId={}", id, userId);
        processor.purge(id);
    }

    public DocumentResponse reprocess(Long userId, Long id) {
        requireEnabled();
        KnowledgeDocument d = owned(userId, id);
        if (d.getStatus() == KnowledgeDocumentStatus.PROCESSING || d.getStatus() == KnowledgeDocumentStatus.UPLOADED) {
            throw ApiException.conflict("DOCUMENT_BUSY", "This document is already being processed.");
        }
        log.info("Knowledge document reprocess requested documentId={} userId={}", id, userId);
        dispatch(id);
        d.setStatus(KnowledgeDocumentStatus.PROCESSING);
        return toResponse(d);
    }

    public AccessResponse access(Long userId, Long id) {
        KnowledgeDocument d = owned(userId, id);
        String token = jwtService.generateKnowledgeToken(userId, id, props.getAccessTokenTtlSeconds());
        String base = "/api/ai/knowledge/documents/" + id;
        return new AccessResponse(id, KnowledgeFileInspector.viewable(d.getFileType()) ? base + "/view?mt=" + token : null,
                base + "/download?mt=" + token, props.getAccessTokenTtlSeconds());
    }

    public Loaded load(Long userId, Long id) {
        KnowledgeDocument d = owned(userId, id);
        Resource r = storage.retrieve(d.getStoragePath()).orElseThrow(KnowledgeService::notFound);
        return new Loaded(d, r);
    }

    public Map<Long, String> readyDocuments(Long userId, List<Long> selected) {
        boolean filter = selected != null && !selected.isEmpty();
        List<Long> ids = filter ? selected.stream().filter(java.util.Objects::nonNull).distinct().limit(500).toList() : List.of(-1L);
        Map<Long, String> out = new LinkedHashMap<>();
        for (KnowledgeDocument d : documents.findReady(userId, filter, ids.isEmpty() ? List.of(-1L) : ids, PageRequest.of(0, Math.max(1, props.getMaxDocumentIds())))) {
            out.put(d.getId(), d.getFileName());
        }
        return out;
    }

    void dispatch(Long documentId) {
        try {
            processor.processAsync(documentId, MDC.get(RequestIdFilter.MDC_KEY));
        } catch (RuntimeException e) {
            log.warn("Knowledge processing queue is full documentId={}", documentId);
        }
    }

    private KnowledgeDocument owned(Long userId, Long id) {
        return documents.findOwned(id, userId).orElseThrow(KnowledgeService::notFound);
    }

    private void requireEnabled() {
        if (!props.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "KNOWLEDGE_DISABLED", "My Knowledge is turned off on this server.");
        }
    }

    private static ApiException notFound() {
        return ApiException.notFound("KNOWLEDGE_DOCUMENT_NOT_FOUND", "Document not found");
    }

    private static MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String safeName(String original, String extension) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).replaceAll("\\p{Cntrl}", "").replaceAll("[<>\"]", "").strip();
        if (name.isEmpty() || name.equals("." + extension)) {
            name = "document." + extension;
        }
        if (name.length() > 255) {
            String suffix = "." + extension;
            name = name.substring(0, 255 - suffix.length()) + suffix;
        }
        return name;
    }

    static DocumentResponse toResponse(KnowledgeDocument d) {
        return new DocumentResponse(d.getId(), d.getFileName(), d.getFileType(), d.getFileSize(), d.getStatus().name(),
                d.getChunkCount(), d.getPageCount(), d.getStatus() == KnowledgeDocumentStatus.FAILED ? d.getErrorMessage() : null,
                d.getCreatedAt(), d.getUpdatedAt());
    }
}
