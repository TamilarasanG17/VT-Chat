package com.vtchat.storage;

import com.vtchat.service.MediaStorageService;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

public class RoutingMediaStorageService implements MediaStorageService {

    private final MediaStorageService primary;
    private final MediaStorageService legacyLocal;

    public RoutingMediaStorageService(MediaStorageService primary, MediaStorageService legacyLocal) {
        this.primary = primary;
        this.legacyLocal = legacyLocal;
    }

    private MediaStorageService of(String key) {
        return CloudinaryRef.isRef(key) ? primary : legacyLocal;
    }

    @Override
    public String store(StoreRequest request, InputStream content) throws IOException {
        return primary.store(request, content);
    }

    @Override
    public Optional<Resource> retrieve(String key) {
        return of(key).retrieve(key);
    }

    @Override
    public Optional<ObjectInfo> head(String key) {
        return of(key).head(key);
    }

    @Override
    public boolean delete(String key) {
        return of(key).delete(key);
    }

    @Override
    public void storeThumbnail(String key, byte[] jpeg) throws IOException {
        of(key).storeThumbnail(key, jpeg);
    }

    @Override
    public Optional<Resource> retrieveThumbnail(String key) {
        return of(key).retrieveThumbnail(key);
    }

    @Override
    public boolean thumbnailExists(String key) {
        return of(key).thumbnailExists(key);
    }

    @Override
    public boolean presignable(String key) {
        return of(key).presignable(key);
    }

    @Override
    public Optional<String> presignGet(String key, Download download) {
        return of(key).presignGet(key, download);
    }

    @Override
    public Optional<String> presignThumbnailGet(String key) {
        return of(key).presignThumbnailGet(key);
    }

    @Override
    public long presignExpirySeconds() {
        return primary.presignExpirySeconds();
    }

    @Override
    public Optional<String> publicUrl(String key) {
        return of(key).publicUrl(key);
    }
}
