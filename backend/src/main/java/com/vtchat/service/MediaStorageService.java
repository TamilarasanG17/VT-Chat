package com.vtchat.service;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public interface MediaStorageService {

    record StoreRequest(String area, long scope, String extension, String contentType, long contentLength) {
        public static StoreRequest chat(long conversationId, String extension, String contentType, long contentLength) {
            return new StoreRequest("chat-media", conversationId, extension, contentType, contentLength);
        }

        public static StoreRequest knowledge(long userId, String extension, String contentType, long contentLength) {
            return new StoreRequest("knowledge", userId, extension, contentType, contentLength);
        }

        public static StoreRequest avatar(long userId, String extension, String contentType, long contentLength) {
            return new StoreRequest("avatars", userId, extension, contentType, contentLength);
        }
    }

    record Download(String contentType, String fileName, boolean inline) {
    }

    record ObjectInfo(long size, String contentType) {
    }

    String store(StoreRequest request, InputStream content) throws IOException;

    Optional<Resource> retrieve(String key);

    Optional<ObjectInfo> head(String key);

    default boolean exists(String key) {
        return head(key).isPresent();
    }

    boolean delete(String key);

    void storeThumbnail(String key, byte[] jpeg) throws IOException;

    Optional<Resource> retrieveThumbnail(String key);

    boolean thumbnailExists(String key);

    boolean presignable(String key);

    Optional<String> presignGet(String key, Download download);

    Optional<String> presignThumbnailGet(String key);

    long presignExpirySeconds();

    default Optional<String> publicUrl(String key) {
        return Optional.empty();
    }
}
