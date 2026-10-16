package com.vtchat.controller;

import com.vtchat.config.MediaStorageProperties;
import com.vtchat.exception.ApiException;
import com.vtchat.service.AvatarStorageService;
import com.vtchat.storage.CloudinaryRef;
import com.vtchat.storage.StorageKeys;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

@RestController
@RequestMapping("/api/avatars")
@RequiredArgsConstructor
public class AvatarController {

    private final AvatarStorageService storage;
    private final MediaStorageProperties props;

    @GetMapping("/c/{userId}/{filename}")
    public ResponseEntity<byte[]> cloud(@PathVariable String userId, @PathVariable String filename) {
        String ref = "cld:iu:" + props.getCloudinary().getFolder().trim() + "/" + StorageKeys.AVATARS + "/" + userId + "/" + filename;
        if (CloudinaryRef.parse(ref).isEmpty()) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
        }
        String url = storage.publicUrl(ref).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found"));
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, url)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
                .build();
    }

    @GetMapping("/{filename}")
    public ResponseEntity<byte[]> legacy(@PathVariable String filename) {
        return respond(storage.load(filename));
    }

    @GetMapping("/{userId}/{filename}")
    public ResponseEntity<byte[]> current(@PathVariable String userId, @PathVariable String filename) {
        String key = StorageKeys.AVATARS + "/" + userId + "/" + filename;
        if (!StorageKeys.isValid(key)) {
            throw new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found");
        }
        return respond(storage.load(key));
    }

    private static ResponseEntity<byte[]> respond(java.util.Optional<byte[]> data) {
        byte[] bytes = data.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found"));
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_JPEG)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .header("Cross-Origin-Resource-Policy", "cross-origin")
                .body(bytes);
    }
}
