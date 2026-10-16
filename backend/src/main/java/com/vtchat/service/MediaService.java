package com.vtchat.service;

import com.vtchat.dto.message.MessageMediaResponse;
import com.vtchat.dto.message.MessageResponse;
import com.vtchat.entity.Message;
import com.vtchat.entity.MessageType;
import com.vtchat.exception.ApiException;
import com.vtchat.repository.MessageDeletionRepository;
import com.vtchat.repository.MessageRepository;
import com.vtchat.security.JwtService;
import com.vtchat.service.MediaFileInspector.Detected;
import com.vtchat.service.MediaStorageService.Download;
import com.vtchat.service.MediaStorageService.StoreRequest;
import com.vtchat.service.MessageService.StoredMedia;
import com.vtchat.storage.LogSanitizer;
import com.vtchat.storage.StorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class MediaService {

    private static final int MAX_NAME_LENGTH = 120;

    private final MediaFileInspector inspector;
    private final MediaStorageService storage;
    private final ImageThumbnailer thumbnailer;
    private final MessageService messageService;
    private final MessageRepository messages;
    private final MessageDeletionRepository deletions;
    private final JwtService jwtService;
    private final MediaDownloadService downloadService;

    public record Loaded(Message message, Resource resource) {
    }

    public enum Variant { INLINE, VIEW, DOWNLOAD, THUMBNAIL }

    public record Served(Message message, Resource resource, String redirectUrl) {
    }

    public record DownloadLink(String url, boolean absolute, long expiresIn) {
    }

    public MessageResponse upload(Long userId, Long conversationId, MultipartFile file, String caption, Long replyToId) {
        messageService.assertCanWrite(conversationId, userId);
        Detected detected = inspector.inspect(file);
        String name = sanitizeName(file.getOriginalFilename(), detected.extension());
        return storeAndCreate(userId, conversationId, file, detected, name, caption, replyToId, null);
    }

    MessageResponse storeAndCreate(Long userId, Long conversationId, MultipartFile file, Detected detected,
                                   String displayName, String caption, Long replyToId, Integer duration) {
        String key;
        try (InputStream in = file.getInputStream()) {
            key = storage.store(StoreRequest.chat(conversationId, detected.extension(), detected.mimeType(), file.getSize()), in);
        } catch (StorageException e) {
            throw storageFailure("upload", e);
        } catch (IOException e) {
            log.error("Could not store an upload operation=upload error={}", LogSanitizer.describe(e));
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "UPLOAD_FAILED", "The file could not be saved");
        }
        try {
            if (detected.type() == MessageType.IMAGE) {
                createThumbnail(key, file);
            }
            return messageService.sendMedia(userId, conversationId,
                    new StoredMedia(detected.type(), key, displayName, file.getSize(), detected.mimeType(), duration),
                    caption, replyToId);
        } catch (RuntimeException e) {
            storage.delete(key);
            throw e;
        }
    }

    private void createThumbnail(String key, MultipartFile file) {
        try (InputStream in = file.getInputStream()) {
            Optional<byte[]> thumb = thumbnailer.create(in);
            if (thumb.isPresent()) {
                storage.storeThumbnail(key, thumb.get());
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Thumbnail skipped: {}", e.getClass().getSimpleName());
        }
    }

    public Loaded load(Long userId, Long messageId, boolean thumbnail) {
        Message message = authorised(userId, messageId);
        Optional<Resource> resource = thumbnail
                ? storage.retrieveThumbnail(message.getMediaKey())
                : storage.retrieve(message.getMediaKey());
        return new Loaded(message, resource.orElseThrow(MediaService::missing));
    }

    public Served serve(Long userId, Long messageId, Variant variant) {
        Message message = authorised(userId, messageId);
        String key = message.getMediaKey();
        boolean thumbnail = variant == Variant.THUMBNAIL;
        if (variant == Variant.DOWNLOAD) {
            requireDownloadAllowed(userId, message);
        }
        try {
            if (storage.presignable(key)) {
                Optional<String> url = thumbnail ? storage.presignThumbnailGet(key) : storage.presignGet(key, downloadSpec(message, variant));
                return new Served(message, null, url.orElseThrow(MediaService::missing));
            }
        } catch (StorageException e) {
            throw storageFailure("serve", e);
        }
        Optional<Resource> resource = thumbnail ? storage.retrieveThumbnail(key) : storage.retrieve(key);
        Resource found = resource.orElseThrow(MediaService::missing);
        if (variant == Variant.DOWNLOAD) {
            recordDownload(userId, message);
        }
        return new Served(message, found, null);
    }

    public DownloadLink downloadLink(Long userId, Long messageId) {
        Message message = authorised(userId, messageId);
        String key = message.getMediaKey();
        requireDownloadAllowed(userId, message);
        try {
            if (storage.presignable(key)) {
                recordDownload(userId, message);
                String url = storage.presignGet(key, downloadSpec(message, Variant.DOWNLOAD)).orElseThrow(MediaService::missing);
                return new DownloadLink(url, true, storage.presignExpirySeconds());
            }
        } catch (StorageException e) {
            throw storageFailure("download-link", e);
        }
        try {
            if (!storage.exists(key)) {
                throw missing();
            }
        } catch (StorageException e) {
            throw storageFailure("download-link", e);
        }
        String token = jwtService.generateMediaToken(userId, messageId);
        return new DownloadLink("/api/media/" + messageId + "/download?mt=" + token, false, JwtService.MEDIA_TOKEN_TTL_SECONDS);
    }

    private void requireDownloadAllowed(Long userId, Message message) {
        if (message.getMessageType() == MessageType.FILE && userId.equals(message.getSender().getId())) {
            throw ApiException.forbidden("DOWNLOAD_NOT_ALLOWED", "You sent this file. You can open it, but only the receiver can download it");
        }
    }

    private void recordDownload(Long userId, Message message) {
        if (message.getMessageType() != MessageType.FILE) {
            return;
        }
        try {
            downloadService.record(message.getId(), userId);
        } catch (RuntimeException e) {
            log.warn("Could not record download of message {}: {}", message.getId(), e.getClass().getSimpleName());
        }
    }

    private Download downloadSpec(Message message, Variant variant) {
        boolean inline = switch (variant) {
            case VIEW -> true;
            case INLINE -> message.getMessageType() != MessageType.FILE;
            default -> false;
        };
        String name = message.getMediaName() == null ? "file" : message.getMediaName();
        return new Download(message.getMimeType(), name, inline);
    }

    public boolean isInlineViewable(Message message) {
        return message.getMessageType() != MessageType.FILE || "application/pdf".equalsIgnoreCase(message.getMimeType());
    }

    public MessageMediaResponse access(Long userId, Long messageId) {
        Message message = authorised(userId, messageId);
        String token = jwtService.generateMediaToken(userId, messageId);
        String base = "/api/media/" + messageId;
        String suffix = "?mt=" + token;
        boolean thumb = message.getMessageType() == MessageType.IMAGE
                && (storage.presignable(message.getMediaKey()) || storage.thumbnailExists(message.getMediaKey()));
        return new MessageMediaResponse(messageId, base + suffix, thumb ? base + "/thumbnail" + suffix : null,
                base + "/download" + suffix, base + "/view" + suffix, JwtService.MEDIA_TOKEN_TTL_SECONDS);
    }

    private Message authorised(Long userId, Long messageId) {
        Message message;
        try {
            message = messageService.requireMessage(messageId, userId);
        } catch (ApiException e) {
            throw missing();
        }
        if (message.isDeleted() || message.getMessageType() == MessageType.TEXT || message.getMediaKey() == null
                || deletions.isHidden(messageId, userId)) {
            throw missing();
        }
        return message;
    }

    private static ApiException missing() {
        return ApiException.notFound("MEDIA_NOT_FOUND", "This file is not available");
    }

    private static ApiException storageFailure(String operation, StorageException e) {
        String op = LogSanitizer.opId();
        log.warn("Media storage failure operation={} category={} op={}", operation, e.kind(), op);
        if (e.kind() == StorageException.Kind.TIMEOUT) {
            return new ApiException(HttpStatus.GATEWAY_TIMEOUT, "STORAGE_TIMEOUT", "File storage took too long to respond. Please try again.");
        }
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "STORAGE_UNAVAILABLE", "File storage is temporarily unavailable. Please try again.");
    }

    public void cleanupIfUnreferenced(Long messageId) {
        try {
            messages.findById(messageId).map(Message::getMediaKey).ifPresent(key -> {
                if (key != null && messages.countByMediaKeyAndDeletedFalse(key) == 0) {
                    storage.delete(key);
                }
            });
        } catch (RuntimeException e) {
            log.warn("Media cleanup skipped for message {}: {}", messageId, e.getClass().getSimpleName());
        }
    }

    static String sanitizeName(String original, String fallbackExtension) {
        String name = original == null ? "" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        name = name.replaceAll("[\\p{Cc}\\p{Cf}\\u2028\\u2029]", "").replaceAll("\\s+", " ").strip();
        while (name.startsWith(".")) {
            name = name.substring(1);
        }
        if (name.isEmpty()) {
            return "file." + fallbackExtension;
        }
        if (name.length() > MAX_NAME_LENGTH) {
            String ext = MediaFileInspector.extensionOf(name);
            String suffix = ext.isEmpty() ? "" : "." + ext;
            name = name.substring(0, MAX_NAME_LENGTH - suffix.length()) + suffix;
        }
        return name;
    }
}
