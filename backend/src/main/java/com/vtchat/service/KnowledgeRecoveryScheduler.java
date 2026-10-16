package com.vtchat.service;

import com.vtchat.config.RagProperties;
import com.vtchat.entity.KnowledgeDocument;
import com.vtchat.entity.KnowledgeDocumentStatus;
import com.vtchat.repository.KnowledgeDocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class KnowledgeRecoveryScheduler {

    private static final String STUCK_MESSAGE = "Processing did not finish. Please try again.";

    private final KnowledgeDocumentRepository documents;
    private final KnowledgeProcessor processor;
    private final RagProperties props;

    @Scheduled(fixedDelayString = "${rag.recovery-interval-ms:60000}", initialDelayString = "${rag.recovery-interval-ms:60000}")
    public void recover() {
        if (!props.isEnabled()) {
            return;
        }
        try {
            Instant now = Instant.now();
            Instant stuckBefore = now.minus(Duration.ofMinutes(Math.max(1, props.getStuckAfterMinutes())));
            for (KnowledgeDocument d : documents.findStale(KnowledgeDocumentStatus.PROCESSING, stuckBefore, PageRequest.of(0, 50))) {
                if (documents.failStuck(d.getId(), stuckBefore, STUCK_MESSAGE, now) > 0) {
                    log.warn("Knowledge document was stuck in PROCESSING and was failed documentId={}", d.getId());
                }
            }
            List<KnowledgeDocument> queued = documents.findStale(KnowledgeDocumentStatus.UPLOADED, now.minusSeconds(120), PageRequest.of(0, 50));
            for (KnowledgeDocument d : queued) {
                log.info("Re-queueing knowledge document documentId={}", d.getId());
                try {
                    processor.processAsync(d.getId(), null);
                } catch (RuntimeException e) {
                    log.warn("Knowledge processing queue is still full");
                    break;
                }
            }
            for (KnowledgeDocument d : documents.findStale(KnowledgeDocumentStatus.DELETED, now.minusSeconds(60), PageRequest.of(0, 50))) {
                processor.purge(d.getId());
            }
        } catch (RuntimeException e) {
            log.warn("Knowledge recovery run failed error={}", e.getClass().getSimpleName());
        }
    }
}
