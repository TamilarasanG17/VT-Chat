package com.vtchat.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudinaryRefTest {

    private static final String UUID = "3f2c1a9e-7b4d-4c1e-9a55-0123456789ab";

    @Test
    void parsesEveryResourceTypeAndKeepsMetadataInTheReference() {
        var image = CloudinaryRef.parse("cld:ia:vtchat/chat-media/42/" + UUID + ".jpg").orElseThrow();
        assertEquals("image", image.resourceType());
        assertEquals("authenticated", image.deliveryType());
        assertEquals("vtchat/chat-media/42/" + UUID, image.publicId());
        assertEquals("jpg", image.format());

        var video = CloudinaryRef.parse("cld:va:vtchat/chat-media/42/" + UUID + ".mp4").orElseThrow();
        assertEquals("video", video.resourceType());

        var raw = CloudinaryRef.parse("cld:ra:vtchat/chat-media/42/" + UUID + ".pdf").orElseThrow();
        assertTrue(raw.isRaw());
        assertEquals("vtchat/chat-media/42/" + UUID + ".pdf", raw.publicId());
        assertEquals(null, raw.format());

        var avatar = CloudinaryRef.parse("cld:iu:vtchat/avatars/7/" + UUID + ".jpg").orElseThrow();
        assertTrue(avatar.isPublic());
        assertEquals("avatars/7/" + UUID + ".jpg", avatar.tail());
    }

    @Test
    void keyRoundTrips() {
        for (String key : new String[]{"cld:ia:vtchat/chat-media/42/" + UUID + ".jpg", "cld:ra:a/b/knowledge/3/" + UUID + ".docx",
                "cld:iu:vtchat/avatars/7/" + UUID + ".jpg"}) {
            assertEquals(key, CloudinaryRef.parse(key).orElseThrow().key());
        }
    }

    @Test
    void tamperedOrForeignReferencesAreRejected() {
        for (String bad : new String[]{null, "", "chat-media/1/" + UUID + ".jpg", "cld:", "cld:xa:vtchat/chat-media/1/" + UUID + ".jpg",
                "cld:ia:vtchat/../chat-media/1/" + UUID + ".jpg", "cld:ia:vtchat/other/1/" + UUID + ".jpg",
                "cld:ia:vtchat/chat-media/x/" + UUID + ".jpg", "cld:ia:vtchat/chat-media/1/not-a-uuid.jpg",
                "cld:ra:vtchat/chat-media/1/" + UUID, "cld:ia:vtchat/chat-media/1/" + UUID,
                "cld:ia:vtchat/chat-media/1/" + UUID + ".jpg?x=1", "cld:ia:https://evil/chat-media/1/" + UUID + ".jpg",
                "cld:ia:/chat-media/1/" + UUID + ".jpg", "cld:ia:chat-media/1/" + UUID + ".jpg"}) {
            assertTrue(CloudinaryRef.parse(bad).isEmpty(), String.valueOf(bad));
        }
    }

    @Test
    void referencesBuiltFromAnUploadResponseAreValidatedToo() {
        assertTrue(CloudinaryRef.of("image", "authenticated", "vtchat/chat-media/1/" + UUID, "png").isPresent());
        assertTrue(CloudinaryRef.of("raw", "authenticated", "vtchat/chat-media/1/" + UUID + ".pdf", null).isPresent());
        assertTrue(CloudinaryRef.of("image", "private", "vtchat/chat-media/1/" + UUID, "png").isEmpty());
        assertTrue(CloudinaryRef.of("auto", "authenticated", "vtchat/chat-media/1/" + UUID, "png").isEmpty());
        assertTrue(CloudinaryRef.of("image", "authenticated", "../x", "png").isEmpty());
    }

    @Test
    void publicIdsAreServerGeneratedFromSafeParts() {
        assertEquals("vtchat/chat-media/5/" + UUID, CloudinaryRef.newPublicId("vtchat", "chat-media", 5, UUID, "jpg", false));
        assertEquals("vtchat/chat-media/5/" + UUID + ".pdf", CloudinaryRef.newPublicId("vtchat", "chat-media", 5, UUID, "pdf", true));
        assertThrows(IllegalArgumentException.class, () -> CloudinaryRef.newPublicId("../x", "chat-media", 5, UUID, "jpg", false));
        assertThrows(IllegalArgumentException.class, () -> CloudinaryRef.newPublicId("vtchat", "elsewhere", 5, UUID, "jpg", false));
        assertThrows(IllegalArgumentException.class, () -> CloudinaryRef.newPublicId("vtchat", "chat-media", 5, "not-a-uuid", "jpg", false));
        assertTrue(CloudinaryRef.validFolder("vtchat/prod"));
        assertFalse(CloudinaryRef.validFolder("vt chat"));
        assertFalse(CloudinaryRef.validFolder("/vtchat"));
    }

    @Test
    void toStringNeverShowsThePublicId() {
        assertFalse(CloudinaryRef.parse("cld:ia:vtchat/chat-media/42/" + UUID + ".jpg").orElseThrow().toString().contains(UUID));
    }
}
