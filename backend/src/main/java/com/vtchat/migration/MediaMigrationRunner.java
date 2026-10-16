package com.vtchat.migration;

import com.vtchat.entity.MediaMigrationItem;
import com.vtchat.repository.MediaMigrationItemRepository;
import com.vtchat.repository.MessageRepository;
import com.vtchat.service.LocalMediaStorageService;
import com.vtchat.service.MediaStorageService.StoreRequest;
import com.vtchat.storage.CloudinaryMediaStorageService;
import com.vtchat.storage.StorageKeys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.InputStream;
import java.time.Instant;
import java.util.List;

@Component
@ConditionalOnProperty(prefix = "media-migration", name = "enabled", havingValue = "true")
public class MediaMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(MediaMigrationRunner.class);

    private final CloudinaryMediaStorageService cloud;
    private final LocalMediaStorageService local;
    private final MessageRepository messages;
    private final MediaMigrationItemRepository items;
    private final TransactionTemplate tx;
    private final ApplicationContext context;
    private final boolean dryRun;
    private final boolean retryFailed;
    private final int limit;
    private final boolean exitWhenDone;

    public MediaMigrationRunner(ObjectProvider<CloudinaryMediaStorageService> cloud, LocalMediaStorageService local,
            MessageRepository messages,
            MediaMigrationItemRepository items, TransactionTemplate tx, ApplicationContext context,
            @Value("${media-migration.dry-run:true}") boolean dryRun,
            @Value("${media-migration.retry-failed:false}") boolean retryFailed,
            @Value("${media-migration.limit:0}") int limit,
            @Value("${media-migration.exit-when-done:true}") boolean exitWhenDone) {
        this.cloud = cloud.getIfAvailable();
        this.local = local;
        this.messages = messages;
        this.items = items;
        this.tx = tx;
        this.context = context;
        this.dryRun = dryRun;
        this.retryFailed = retryFailed;
        this.limit = limit;
        this.exitWhenDone = exitWhenDone;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (cloud == null) {
            throw new IllegalStateException("MEDIA_MIGRATION_ENABLED=true needs MEDIA_STORAGE_PROVIDER=cloudinary");
        }
        List<Object[]> legacy = messages.findLegacyMediaKeys();
        log.info("Media migration started dryRun={} candidates={} limit={}", dryRun, legacy.size(), limit);
        int done = 0, failed = 0, skipped = 0, processed = 0;
        for (Object[] row : legacy) {
            if (limit > 0 && processed >= limit)
                break;
            String oldKey = (String) row[0];
            long conversationId = ((Number) row[1]).longValue();
            String mime = row[2] == null ? "application/octet-stream" : (String) row[2];
            if (StorageKeys.uuid(oldKey).isEmpty() || StorageKeys.extension(oldKey).isEmpty()) {
                skipped++;
                log.warn("Media migration skipped: reference is not a known local layout");
                continue;
            }
            MediaMigrationItem item = items.findByLegacyKey(oldKey).orElse(null);
            if (item != null && (item.getStatus() == MediaMigrationItem.Status.DONE
                    || (item.getStatus() == MediaMigrationItem.Status.FAILED && !retryFailed))) {
                skipped++;
                continue;
            }
            processed++;
            if (dryRun) {
                log.info("Media migration DRY-RUN would copy 1 file (conversationId={})", conversationId);
                continue;
            }
            if (item == null) {
                item = new MediaMigrationItem();
                item.setLegacyKey(oldKey);
                item.setNewKey(oldKey);
            }
            try {
                migrateOne(item, conversationId, mime);
                done++;
            } catch (Exception e) {
                failed++;
                item.setStatus(MediaMigrationItem.Status.FAILED);
                item.setAttempts(item.getAttempts() + 1);
                item.setLastError(e.getClass().getSimpleName());
                item.setUpdatedAt(Instant.now());
                items.save(item);
                log.warn("Media migration failed itemId={} error={}", item.getId(), e.getClass().getSimpleName());
            }
        }
        log.info("Media migration finished done={} failed={} skipped={} dryRun={}", done, failed, skipped, dryRun);

        if (exitWhenDone) {
            int exitCode = failed > 0 ? 1 : 0;
            System.exit(SpringApplication.exit(context, () -> exitCode));
        }
    }

    private void migrateOne(MediaMigrationItem item, long conversationId, String mime) throws Exception {
        String oldKey = item.getLegacyKey();
        Resource source = local.retrieve(oldKey).orElseThrow(() -> new IllegalStateException("local file missing"));
        long size = source.contentLength();
        String uuid = StorageKeys.uuid(oldKey).orElseThrow();
        String ext = StorageKeys.extension(oldKey).orElseThrow();
        String newKey;
        try (InputStream in = source.getInputStream()) {
            newKey = cloud.storeDeterministic(StoreRequest.chat(conversationId, ext, mime, size), uuid, in);
        }
        tx.executeWithoutResult(s -> {
            int updated = messages.repointMediaKey(oldKey, newKey);
            item.setNewKey(newKey);
            item.setStatus(MediaMigrationItem.Status.DONE);
            item.setAttempts(item.getAttempts() + 1);
            item.setSizeBytes(size);
            item.setLastError(null);
            item.setUpdatedAt(Instant.now());
            items.save(item);
            log.info("Media migration repointed messages={} itemId={}", updated, item.getId());
        });
    }
}
