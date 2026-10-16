package com.vtchat.service;

import com.vtchat.storage.RoutingMediaStorageService;
import com.vtchat.storage.StorageKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalMediaStorageServiceTest {

    private static final String UUID = "3f2c1a9e-7b4d-4c1e-9a55-0123456789ab";

    @TempDir Path root;
    private LocalMediaStorageService local;

    @BeforeEach
    void setUp() {
        local = new LocalMediaStorageService(root);
    }

    private String store(MediaStorageService.StoreRequest req, byte[] bytes) throws Exception {
        return local.store(req, new ByteArrayInputStream(bytes));
    }

    @Test
    void everyMediaCategoryRoundTripsAndDeletes() throws Exception {
        byte[] data = {1, 2, 3, 4};
        String[] keys = {
                store(MediaStorageService.StoreRequest.chat(12, "jpg", "image/jpeg", 4), data),
                store(MediaStorageService.StoreRequest.chat(12, "mp4", "video/mp4", 4), data),
                store(MediaStorageService.StoreRequest.chat(12, "webm", "audio/webm", 4), data),
                store(MediaStorageService.StoreRequest.chat(12, "pdf", "application/pdf", 4), data),
                store(MediaStorageService.StoreRequest.chat(12, "docx", "application/octet-stream", 4), data),
                store(MediaStorageService.StoreRequest.knowledge(3, "txt", "text/plain", 4), data),
                store(MediaStorageService.StoreRequest.avatar(7, "jpg", "image/jpeg", 4), data),
        };
        for (String key : keys) {
            assertTrue(StorageKeys.isValid(key), key);
            assertArrayEquals(data, local.retrieve(key).orElseThrow().getInputStream().readAllBytes());
            assertEquals(4, local.head(key).orElseThrow().size());
            assertTrue(local.delete(key));
            assertTrue(local.retrieve(key).isEmpty(), "gone after delete");
            assertTrue(local.delete(key), "deleting twice is fine");
        }
        assertTrue(keys[6].startsWith("avatars/7/"));
    }

    @Test
    void thumbnailIsDeletedWithItsImage() throws Exception {
        String key = store(MediaStorageService.StoreRequest.chat(12, "png", "image/png", 1), new byte[]{1});
        local.storeThumbnail(key, new byte[]{9});
        assertTrue(local.thumbnailExists(key));
        local.delete(key);
        assertFalse(local.thumbnailExists(key));
    }

    @Test
    void keysFromClientsOrTraversalAreNeverResolved() {
        for (String bad : new String[]{"../../etc/passwd", "avatars/../../x", "/etc/passwd", "chat-media/1/../../../x.jpg", ""}) {
            assertTrue(local.retrieve(bad).isEmpty(), bad);
            assertTrue(local.head(bad).isEmpty(), bad);
        }
    }

    @Test
    void legacyFilesStayReadableThroughTheRoutingStorage() throws Exception {
        Files.createDirectories(root.resolve("images"));
        Files.write(root.resolve("images").resolve(UUID + ".png"), new byte[]{7, 7});
        MediaStorageService fakeCloud = org.mockito.Mockito.mock(MediaStorageService.class);
        RoutingMediaStorageService routing = new RoutingMediaStorageService(fakeCloud, local);
        assertEquals(2, routing.retrieve("images/" + UUID + ".png").orElseThrow().contentLength());
        org.mockito.Mockito.verifyNoInteractions(fakeCloud);
        routing.retrieve("chat-media/1/" + UUID + ".png");
        org.mockito.Mockito.verifyNoInteractions(fakeCloud);
        String cld = "cld:ia:vtchat/chat-media/1/" + UUID + ".png";
        routing.retrieve(cld);
        org.mockito.Mockito.verify(fakeCloud).retrieve(cld);
    }
}
