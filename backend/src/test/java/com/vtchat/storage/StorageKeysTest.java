package com.vtchat.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StorageKeysTest {

    private static final String UUID = "3f2c1a9e-7b4d-4c1e-9a55-0123456789ab";

    @Test
    void newKeysUseTheDedicatedNamespacesAndNeverTheFileName() {
        assertTrue(StorageKeys.newKey("avatars", 7, "jpg").matches("avatars/7/[0-9a-f-]{36}\\.jpg"));
        assertTrue(StorageKeys.newKey("chat-media", 12, "pdf").startsWith("chat-media/12/"));
        assertTrue(StorageKeys.newKey("knowledge", 3, "docx").startsWith("knowledge/3/"));
        assertThrows(IllegalArgumentException.class, () -> StorageKeys.newKey("other", 1, "jpg"));
        assertThrows(IllegalArgumentException.class, () -> StorageKeys.newKey("avatars", 1, "../x"));
    }

    @Test
    void pathTraversalAndForeignKeysAreRejected() {
        for (String bad : new String[]{"../etc/passwd", "avatars/1/../../x.jpg", "avatars/1/" + UUID + ".jpg/../x",
                "/avatars/1/" + UUID + ".jpg", "avatars//" + UUID + ".jpg", "avatars/x/" + UUID + ".jpg", "secrets/1/" + UUID + ".jpg",
                "avatars/1/not-a-uuid.jpg", "", null}) {
            assertFalse(StorageKeys.isValid(bad), String.valueOf(bad));
        }
        assertTrue(StorageKeys.isValid("avatars/1/" + UUID + ".jpg"));
        assertTrue(StorageKeys.isValid("images/" + UUID + ".png"));
    }

    @Test
    void legacyKeysStayLocalAndNewKeysDoNot() {
        assertTrue(StorageKeys.isLegacy("images/" + UUID + ".png"));
        assertFalse(StorageKeys.isLegacy("chat-media/1/" + UUID + ".png"));
        assertFalse(StorageKeys.isLegacy("avatars/1/" + UUID + ".jpg"));
    }

    @Test
    void onlyChatMediaHasThumbnails() {
        assertEquals("chat-media/4/thumbs/" + UUID + ".jpg", StorageKeys.thumbnailKey("chat-media/4/" + UUID + ".png").orElseThrow());
        assertTrue(StorageKeys.thumbnailKey("avatars/4/" + UUID + ".jpg").isEmpty());
        assertTrue(StorageKeys.thumbnailKey("knowledge/4/" + UUID + ".pdf").isEmpty());
    }

    @Test
    void uuidAndExtensionComeFromAnyLocalKeyLayout() {
        assertEquals(UUID, StorageKeys.uuid("files/" + UUID + ".pdf").orElseThrow());
        assertEquals(UUID, StorageKeys.uuid("chat-media/9/" + UUID + ".pdf").orElseThrow());
        assertEquals("pdf", StorageKeys.extension("files/" + UUID + ".pdf").orElseThrow());
        assertTrue(StorageKeys.uuid("cld:ra:vtchat/chat-media/9/" + UUID + ".pdf").isEmpty());
    }
}
