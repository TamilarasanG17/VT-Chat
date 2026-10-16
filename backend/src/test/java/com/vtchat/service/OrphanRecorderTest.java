package com.vtchat.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.vtchat.entity.MediaOrphan;
import com.vtchat.repository.MediaOrphanRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrphanRecorderTest {

    private static final String KEY = "chat-media/12/3f2c1a9e-7b4d-4c1e-9a55-0123456789ab.pdf";

    private ListAppender<ILoggingEvent> attach() {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        Logger logger = (Logger) LoggerFactory.getLogger("media.orphans");
        logger.setLevel(Level.DEBUG);
        logger.addAppender(appender);
        return appender;
    }

    @Test
    void keyGoesToTheTableAndNeverToTheLog() {
        MediaOrphanRepository repo = mock(MediaOrphanRepository.class);
        when(repo.save(any(MediaOrphan.class))).thenAnswer(i -> {
            MediaOrphan o = i.getArgument(0);
            o.setId(41L);
            return o;
        });
        ListAppender<ILoggingEvent> logs = attach();
        new OrphanRecorder(repo).record("cloudinary", KEY, "TIMEOUT");
        ArgumentCaptor<MediaOrphan> saved = ArgumentCaptor.forClass(MediaOrphan.class);
        verify(repo).save(saved.capture());
        assertEquals(KEY, saved.getValue().getObjectKey());
        assertEquals("TIMEOUT", saved.getValue().getCategory());
        String text = logs.list.get(0).getFormattedMessage();
        assertTrue(text.contains("orphanId=41") && text.contains("provider=cloudinary"), text);
        assertFalse(text.contains("chat-media") || text.contains("3f2c1a9e"), text);
    }

    @Test
    void evenWhenTheTableIsDownTheKeyIsNotLogged() {
        MediaOrphanRepository repo = mock(MediaOrphanRepository.class);
        when(repo.save(any(MediaOrphan.class))).thenThrow(new IllegalStateException("db down at jdbc:mysql://host/" + KEY));
        ListAppender<ILoggingEvent> logs = attach();
        new OrphanRecorder(repo).record("local", KEY, "FAILED");
        String text = logs.list.get(logs.list.size() - 1).getFormattedMessage();
        assertTrue(text.contains("ORPHANED_MEDIA_UNRECORDED"), text);
        assertFalse(text.contains("chat-media") || text.contains("jdbc") || text.contains("3f2c1a9e"), text);
    }
}
