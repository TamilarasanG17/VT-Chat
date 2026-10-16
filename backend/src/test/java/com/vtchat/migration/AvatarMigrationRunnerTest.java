package com.vtchat.migration;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AvatarMigrationRunnerTest {

    private static final String UUID = "3f2c1a9e-7b4d-4c1e-9a55-0123456789ab";

    @Test
    void recognisesEveryLegacyReferenceShape() {
        assertEquals(UUID + ".jpg", AvatarMigrationRunner.legacyName(UUID + ".jpg"));
        assertEquals(UUID + ".jpg", AvatarMigrationRunner.legacyName("avatars/" + UUID + ".jpg"));
        assertEquals(UUID + ".jpg", AvatarMigrationRunner.legacyName("uploads/avatars/" + UUID + ".jpg"));
        assertEquals(UUID + ".jpg", AvatarMigrationRunner.legacyName("http://localhost:8080/api/avatars/" + UUID + ".jpg"));
        assertEquals(UUID + ".jpg", AvatarMigrationRunner.legacyName("/api/avatars/" + UUID + ".jpg?x=1"));
    }

    @Test
    void rejectsAnythingElse() {
        assertNull(AvatarMigrationRunner.legacyName(null));
        assertNull(AvatarMigrationRunner.legacyName(""));
        assertNull(AvatarMigrationRunner.legacyName("me.jpg"));
        assertNull(AvatarMigrationRunner.legacyName(UUID + ".exe"));
    }
}
