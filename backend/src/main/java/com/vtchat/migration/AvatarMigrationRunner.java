package com.vtchat.migration;

import com.vtchat.entity.MediaMigrationItem;
import com.vtchat.repository.MediaMigrationItemRepository;
import com.vtchat.repository.UserRepository;
import com.vtchat.service.AvatarStorageService;
import com.vtchat.service.MediaStorageService.StoreRequest;
import com.vtchat.storage.LogSanitizer;
import com.vtchat.storage.CloudinaryMediaStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@ConditionalOnProperty(prefix = "avatar-migration", name = "enabled", havingValue = "true")
public class AvatarMigrationRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AvatarMigrationRunner.class);
    private static final Pattern NAME = Pattern
            .compile("([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})\\.jpg$");

    private final CloudinaryMediaStorageService cloud;
    private final AvatarStorageService avatars;
    private final UserRepository users;
    private final MediaMigrationItemRepository items;
    private final ApplicationContext context;
    private final boolean dryRun;
    private final boolean retryFailed;
    private final int limit;
    private final boolean exitWhenDone;

    public AvatarMigrationRunner(ObjectProvider<CloudinaryMediaStorageService> cloud, AvatarStorageService avatars,
            UserRepository users,
            MediaMigrationItemRepository items, ApplicationContext context,
            @Value("${avatar-migration.dry-run:true}") boolean dryRun,
            @Value("${avatar-migration.retry-failed:false}") boolean retryFailed,
            @Value("${avatar-migration.limit:0}") int limit,
            @Value("${avatar-migration.exit-when-done:true}") boolean exitWhenDone) {
        this.cloud = cloud.getIfAvailable();
        this.avatars = avatars;
        this.users = users;
        this.items = items;
        this.context = context;
        this.dryRun = dryRun;
        this.retryFailed = retryFailed;
        this.limit = limit;
        this.exitWhenDone = exitWhenDone;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (cloud == null) {
            throw new IllegalStateException("AVATAR_MIGRATION_ENABLED=true needs MEDIA_STORAGE_PROVIDER=cloudinary");
        }
        List<Object[]> rows = users.findLegacyAvatars();
        log.info("Avatar migration started dryRun={} candidates={} limit={}", dryRun, rows.size(), limit);
        int done = 0, failed = 0, skipped = 0, processed = 0;
        for (Object[] row : rows) {
            if (limit > 0 && processed >= limit)
                break;
            long userId = ((Number) row[0]).longValue();
            String stored = (String) row[1];
            String name = legacyName(stored);
            if (name == null) {
                skipped++;
                log.warn("Avatar migration skipped: reference is not a recognised local avatar userId={}", userId);
                continue;
            }
            String legacyKey = "avatar-legacy/" + name;
            MediaMigrationItem item = items.findByLegacyKey(legacyKey).orElse(null);
            if (item != null && (item.getStatus() == MediaMigrationItem.Status.DONE
                    || (item.getStatus() == MediaMigrationItem.Status.FAILED && !retryFailed))) {
                skipped++;
                continue;
            }
            processed++;
            if (dryRun) {
                log.info("Avatar migration DRY-RUN would copy 1 avatar userId={}", userId);
                continue;
            }
            if (item == null) {
                item = new MediaMigrationItem();
                item.setLegacyKey(legacyKey);
                item.setNewKey(stored);
            }
            try {
                migrateOne(item, userId, stored, name);
                done++;
            } catch (Exception e) {
                failed++;
                item.setStatus(MediaMigrationItem.Status.FAILED);
                item.setAttempts(item.getAttempts() + 1);
                item.setLastError(LogSanitizer.redact(e.getClass().getSimpleName()));
                item.setUpdatedAt(Instant.now());
                items.save(item);
                log.warn("Avatar migration failed userId={} error={}", userId, LogSanitizer.describe(e));
            }
        }
        log.info("Avatar migration finished done={} failed={} skipped={} dryRun={}", done, failed, skipped, dryRun);

        if (exitWhenDone) {
            int exitCode = failed > 0 ? 1 : 0;
            System.exit(SpringApplication.exit(context, () -> exitCode));
        }
    }

    static String legacyName(String stored) {
        if (stored == null) {
            return null;
        }
        String s = stored.trim();
        int q = s.indexOf('?');
        if (q >= 0) {
            s = s.substring(0, q);
        }
        Matcher m = NAME.matcher(s);
        return m.find() ? m.group(1) + ".jpg" : null;
    }

    private void migrateOne(MediaMigrationItem item, long userId, String stored, String name) throws Exception {
        byte[] bytes = avatars.readLocal(stored).or(() -> avatars.readLegacy(name))
                .orElseThrow(() -> new IllegalStateException("local file missing"));
        if (bytes.length < 4 || (bytes[0] & 0xFF) != 0xFF || (bytes[1] & 0xFF) != 0xD8 || (bytes[2] & 0xFF) != 0xFF) {
            throw new IllegalStateException("not a JPEG");
        }
        if (javax.imageio.ImageIO.read(new ByteArrayInputStream(bytes)) == null) {
            throw new IllegalStateException("not a decodable image");
        }
        String newKey = cloud.storeDeterministic(StoreRequest.avatar(userId, "jpg", "image/jpeg", bytes.length),
                name.substring(0, name.length() - 4), new ByteArrayInputStream(bytes));
        int changed = users.replaceProfilePicture(userId, stored, newKey);
        item.setNewKey(newKey);
        item.setStatus(MediaMigrationItem.Status.DONE);
        item.setAttempts(item.getAttempts() + 1);
        item.setSizeBytes((long) bytes.length);
        item.setLastError(changed == 0 ? "SUPERSEDED" : null);
        item.setUpdatedAt(Instant.now());
        items.save(item);
        log.info("Avatar migration copied userId={} repointed={}", userId, changed > 0);
    }
}
