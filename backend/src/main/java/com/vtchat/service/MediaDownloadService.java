package com.vtchat.service;

import com.vtchat.entity.MediaDownload;
import com.vtchat.repository.MediaDownloadRepository;
import com.vtchat.repository.MessageRepository;
import com.vtchat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class MediaDownloadService {

    private final MediaDownloadRepository downloads;
    private final MessageRepository messages;
    private final UserRepository users;

    public void record(Long messageId, Long userId) {
        Instant now = Instant.now();
        if (downloads.touch(messageId, userId, now) > 0) {
            return;
        }
        try {
            downloads.saveAndFlush(MediaDownload.builder()
                    .message(messages.getReferenceById(messageId))
                    .user(users.getReferenceById(userId))
                    .downloadedAt(now)
                    .build());
        } catch (DataIntegrityViolationException e) {
            downloads.touch(messageId, userId, now);
        }
    }
}
