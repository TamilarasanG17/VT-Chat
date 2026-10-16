package com.vtchat.service;

import com.vtchat.dto.message.MessageResponse;
import com.vtchat.exception.ApiException;
import com.vtchat.service.MediaFileInspector.Detected;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class VoiceMessageService {

    private static final double MAX_DURATION_SECONDS = 3600;

    private final MediaFileInspector inspector;
    private final MediaService mediaService;
    private final MessageService messageService;

    public MessageResponse upload(Long userId, Long conversationId, MultipartFile file, Double durationSeconds, Long replyToId) {
        messageService.assertCanWrite(conversationId, userId);
        if (durationSeconds == null || durationSeconds.isNaN() || durationSeconds <= 0 || durationSeconds > MAX_DURATION_SECONDS) {
            throw ApiException.badRequest("INVALID_DURATION", "The voice message length is not valid");
        }
        Detected detected = inspector.inspectVoice(file);
        int seconds = Math.max(1, (int) Math.round(durationSeconds));
        return mediaService.storeAndCreate(userId, conversationId, file, detected,
                "Voice message." + detected.extension(), null, replyToId, seconds);
    }
}
