package com.vtchat.service;

import com.vtchat.entity.MediaOrphan;
import com.vtchat.repository.MediaOrphanRepository;
import com.vtchat.storage.LogSanitizer;
import com.vtchat.storage.OrphanSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrphanRecorder implements OrphanSink {

    private static final Logger ORPHANS = LoggerFactory.getLogger("media.orphans");

    private final MediaOrphanRepository repository;

    public OrphanRecorder(MediaOrphanRepository repository) {
        this.repository = repository;
    }

    @Override
    public void record(String provider, String key, String category) {
        try {
            MediaOrphan row = new MediaOrphan();
            row.setProvider(provider);
            row.setObjectKey(key);
            row.setCategory(category);
            MediaOrphan saved = repository.save(row);
            ORPHANS.error("ORPHANED_MEDIA provider={} category={} orphanId={}", provider, category, saved.getId());
        } catch (RuntimeException e) {
            ORPHANS.error("ORPHANED_MEDIA_UNRECORDED provider={} category={} op={} error={}", provider, category,
                    LogSanitizer.opId(), LogSanitizer.describe(e));
        }
    }
}
