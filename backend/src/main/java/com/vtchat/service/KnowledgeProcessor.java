package com.vtchat.service;

import com.vtchat.ai.AiServiceClient;
import com.vtchat.ai.AiServiceException;
import com.vtchat.config.RequestIdFilter;
import com.vtchat.entity.KnowledgeDocument;
import com.vtchat.entity.KnowledgeDocumentStatus;
import com.vtchat.repository.KnowledgeDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.io.Resource;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeProcessor {

    private final KnowledgeDocumentRepository documents;
    private final MediaStorageService storage;
    private final AiServiceClient client;

    @Async("ragExecutor")
    public void processAsync(Long documentId, String requestId) {
        if (requestId != null) {
            MDC.put(RequestIdFilter.MDC_KEY, requestId);
        }
        try {
            process(documentId);
        } catch (RuntimeException e) {
            log.error("Knowledge processing crashed documentId={} error={}", documentId, e.getClass().getSimpleName());
        } finally {
            MDC.remove(RequestIdFilter.MDC_KEY);
        }
    }

    void process(Long documentId) {
        int claimed = documents.claim(documentId,
                EnumSet.of(KnowledgeDocumentStatus.UPLOADED, KnowledgeDocumentStatus.FAILED, KnowledgeDocumentStatus.READY), Instant.now());
        if (claimed == 0) {
            log.info("Knowledge processing skipped documentId={} (already processing, deleted or gone)", documentId);
            return;
        }
        KnowledgeDocument d = documents.findWithUser(documentId).orElse(null);
        if (d == null || d.getStatus() != KnowledgeDocumentStatus.PROCESSING) {
            return;
        }
        long userId = d.getUser().getId();
        int version = d.getProcessingVersion();
        String rid = MDC.get(RequestIdFilter.MDC_KEY);
        long started = System.currentTimeMillis();
        log.info("Knowledge processing started documentId={} userId={} version={} type={} bytes={}", documentId, userId, version, d.getFileType(), d.getFileSize());
        try {
            Optional<Resource> file = storage.retrieve(d.getStoragePath());
            if (file.isEmpty()) {
                fail(d, version, "The stored file is missing. Please upload the document again.", false);
                return;
            }
            AiServiceClient.ProcessResult result;
            try (InputStream in = file.get().getInputStream()) {
                result = client.processDocument(userId, documentId, version, d.getFileType(), d.getFileName(), in, file.get().contentLength(), rid);
            }
            if (result.chunkCount() < 1) {
                fail(d, version, "No readable text was found in this document.", true);
                return;
            }
            int done = documents.finish(documentId, version, KnowledgeDocumentStatus.READY, result.chunkCount(), result.pageCount(), null, Instant.now());
            if (done == 0) {
                log.info("Knowledge processing superseded documentId={} version={}", documentId, version);
                if (documents.findById(documentId).map(x -> x.getStatus() == KnowledgeDocumentStatus.DELETED).orElse(true)) {
                    purge(documentId);
                }
                return;
            }
            log.info("Knowledge processing completed documentId={} userId={} chunks={} pages={} ms={}", documentId, userId,
                    result.chunkCount(), result.pageCount(), System.currentTimeMillis() - started);
        } catch (AiServiceException e) {
            log.warn("Knowledge processing failed documentId={} version={} kind={} code={}", documentId, version, e.kind(), e.code());
            fail(d, version, messageFor(e), e.kind() != AiServiceException.Kind.RAG_UNAVAILABLE);
        } catch (Exception e) {
            log.warn("Knowledge processing failed documentId={} version={} error={}", documentId, version, e.getClass().getSimpleName());
            fail(d, version, "The document could not be processed. Please try again.", true);
        }
    }

    private void fail(KnowledgeDocument d, int version, String message, boolean cleanVectors) {
        int done = documents.finish(d.getId(), version, KnowledgeDocumentStatus.FAILED, 0, null, message, Instant.now());
        if (done > 0 && cleanVectors) {
            try {
                client.deleteDocumentVectors(d.getUser().getId(), d.getId(), MDC.get(RequestIdFilter.MDC_KEY));
            } catch (RuntimeException e) {
                log.warn("Could not clean vectors of failed document documentId={}", d.getId());
            }
        }
    }

    public void purge(Long documentId) {
        KnowledgeDocument d = documents.findWithUser(documentId).orElse(null);
        if (d == null) {
            return;
        }
        if (d.getStatus() != KnowledgeDocumentStatus.DELETED) {
            return;
        }
        try {
            client.deleteDocumentVectors(d.getUser().getId(), documentId, MDC.get(RequestIdFilter.MDC_KEY));
        } catch (AiServiceException e) {
            log.warn("Vector cleanup deferred documentId={} kind={}", documentId, e.kind());
            return;
        }
        storage.delete(d.getStoragePath());
        documents.deleteById(documentId);
        log.info("Knowledge document removed documentId={}", documentId);
    }

    private static String messageFor(AiServiceException e) {
        return switch (e.code()) {
            case "NO_TEXT" -> "No readable text was found. Scanned or image-only PDFs are not supported (no OCR).";
            case "EMPTY_DOCUMENT" -> "This document contains no readable text.";
            case "DOCUMENT_TOO_LARGE" -> "This document is too large to process.";
            case "ENCRYPTED_PDF" -> "This PDF is password-protected.";
            case "TOO_MANY_PAGES" -> "This PDF has too many pages.";
            case "CORRUPT_DOCUMENT" -> "This file could not be read. It may be damaged.";
            case "UNSUPPORTED_DOC" -> "Old .doc files are not supported on this server. Save the file as .docx and upload it again.";
            case "UNSUPPORTED_TYPE", "UNSUPPORTED_CONTENT" -> "This file does not contain supported text.";
            default -> switch (e.kind()) {
                case RAG_UNAVAILABLE -> "Document search is temporarily unavailable. Please try again later.";
                case TIMEOUT -> "Processing took too long. Please try again.";
                default -> "The document could not be processed. Please try again.";
            };
        };
    }
}
