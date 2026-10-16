package com.vtchat.service;

import com.vtchat.storage.OrphanSink;
import com.vtchat.storage.StorageKeys;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

public class LocalMediaStorageService implements MediaStorageService {

    private final Path root;
    private final OrphanSink orphans;

    public LocalMediaStorageService(Path root) {
        this(root, OrphanSink.NONE);
    }

    public LocalMediaStorageService(Path root, OrphanSink orphans) {
        this.orphans = orphans == null ? OrphanSink.NONE : orphans;
        this.root = root.toAbsolutePath().normalize();
        try {
            for (String dir : new String[]{"images", "images/thumbs", "videos", "files", "voice", "chat-media", "knowledge", "avatars"}) {
                Files.createDirectories(this.root.resolve(dir));
            }
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create media directory " + this.root, e);
        }
    }

    @Override
    public String store(StoreRequest request, InputStream content) throws IOException {
        String key = StorageKeys.newKey(request.area(), request.scope(), request.extension());
        Path target = resolve(key).orElseThrow(() -> new IOException("Invalid storage key"));
        Files.createDirectories(target.getParent());
        try {
            Files.copy(content, target);
        } catch (IOException e) {
            Files.deleteIfExists(target);
            throw e;
        }
        return key;
    }

    @Override
    public Optional<Resource> retrieve(String key) {
        return resolve(key).filter(Files::isRegularFile).map(FileSystemResource::new);
    }

    @Override
    public Optional<ObjectInfo> head(String key) {
        return resolve(key).filter(Files::isRegularFile).map(p -> {
            try {
                return new ObjectInfo(Files.size(p), null);
            } catch (IOException e) {
                return new ObjectInfo(-1, null);
            }
        });
    }

    @Override
    public boolean delete(String key) {
        Optional<Path> path = resolve(key);
        if (path.isEmpty()) {
            return true;
        }
        boolean ok = deleteQuietly(path.get(), key);
        for (Path thumb : StorageKeys.thumbnailKey(key).flatMap(this::resolve).stream().toList()) {
            ok &= deleteQuietly(thumb, StorageKeys.thumbnailKey(key).orElse(key));
        }
        return ok;
    }

    private boolean deleteQuietly(Path path, String key) {
        try {
            Files.deleteIfExists(path);
            return true;
        } catch (IOException e) {
            orphans.record("local", key, "FAILED");
            return false;
        }
    }

    @Override
    public void storeThumbnail(String key, byte[] jpeg) throws IOException {
        Path thumb = StorageKeys.thumbnailKey(key).flatMap(this::resolve).orElseThrow(() -> new IOException("Invalid storage key"));
        Files.createDirectories(thumb.getParent());
        Files.write(thumb, jpeg);
    }

    @Override
    public Optional<Resource> retrieveThumbnail(String key) {
        return StorageKeys.thumbnailKey(key).flatMap(this::resolve).filter(Files::isRegularFile).map(FileSystemResource::new);
    }

    @Override
    public boolean thumbnailExists(String key) {
        return retrieveThumbnail(key).isPresent();
    }

    @Override
    public boolean presignable(String key) {
        return false;
    }

    @Override
    public Optional<String> presignGet(String key, Download download) {
        return Optional.empty();
    }

    @Override
    public Optional<String> presignThumbnailGet(String key) {
        return Optional.empty();
    }

    @Override
    public long presignExpirySeconds() {
        return 0;
    }

    private Optional<Path> resolve(String key) {
        if (!StorageKeys.isValid(key)) {
            return Optional.empty();
        }
        Path path = root.resolve(key).normalize();
        return path.startsWith(root) ? Optional.of(path) : Optional.empty();
    }
}
