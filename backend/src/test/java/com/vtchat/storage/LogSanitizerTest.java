package com.vtchat.storage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogSanitizerTest {

    private static final String UUID = "3f2c1a9e-7b4d-4c1e-9a55-0123456789ab";

    @Test
    void redactsPresignedUrlsAndTheirQueryStrings() {
        String text = "GET https://storage.example.com/bucket/chat-media/5/" + UUID
                + ".pdf?X-Amz-Signature=abcdef&X-Amz-Credential=AKIAABCDEFGHIJKLMNOP%2Fauto failed";
        String out = LogSanitizer.redact(text);
        assertFalse(out.contains("storage.example.com"), out);
        assertFalse(out.contains("X-Amz"), out);
        assertFalse(out.contains("abcdef"), out);
        assertTrue(out.contains("failed"));
    }

    @Test
    void redactsObjectKeysInEveryLayout() {
        for (String key : new String[]{"chat-media/12/" + UUID + ".jpg", "chat-media/12/thumbs/" + UUID + ".jpg",
                "knowledge/3/" + UUID + ".pdf", "avatars/9/" + UUID + ".jpg", "images/" + UUID + ".png",
                "voice/" + UUID + ".webm", "images/thumbs/" + UUID + ".jpg", UUID + ".jpg"}) {
            String out = LogSanitizer.redact("could not delete " + key + " now");
            assertFalse(out.contains(UUID), key + " -> " + out);
        }
    }

    @Test
    void redactsTokensAuthorizationAndSecrets() {
        String jwt = "eyJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxMjMifQ.c2lnbmF0dXJl";
        assertFalse(LogSanitizer.redact("Authorization: Bearer " + jwt).contains("eyJ"));
        assertFalse(LogSanitizer.redact("token=" + jwt).contains("eyJ"));
        assertFalse(LogSanitizer.redact("secret_access_key=Zm9vYmFyYmF6").contains("Zm9v"));
        assertFalse(LogSanitizer.redact("api-key: INTERNAL-KEY-123").contains("INTERNAL-KEY-123"));
        assertFalse(LogSanitizer.redact("AKIAABCDEFGHIJKLMNOP").contains("AKIA"));
        assertFalse(LogSanitizer.redact("/api/media/5?mt=" + jwt).contains("eyJ"));
    }

    @Test
    void redactsCloudinaryReferencesSignaturesAndCredentials() {
        String ref = "cld:ia:vtchat/chat-media/5/" + UUID + ".jpg";
        assertFalse(LogSanitizer.redact("delete " + ref + " failed").contains(UUID));
        String url = "https://res.cloudinary.com/democloud/image/authenticated/s--AbCdEf12--/v1/vtchat/chat-media/5/" + UUID + ".jpg";
        String out = LogSanitizer.redact("GET " + url + " 500");
        assertFalse(out.contains("cloudinary.com") || out.contains("AbCdEf12") || out.contains(UUID), out);
        assertFalse(LogSanitizer.redact("s--AbCdEf12-- in path").contains("AbCdEf12"));
        assertFalse(LogSanitizer.redact("cloudinary://123456789012345:SuperSecretValue@democloud").contains("SuperSecretValue"));
        assertFalse(LogSanitizer.redact("api_secret=SuperSecretValue123").contains("SuperSecretValue123"));
    }

    @Test
    void keepsHarmlessText() {
        assertEquals("Object storage upload failed (TIMEOUT)", LogSanitizer.redact("Object storage upload failed (TIMEOUT)"));
        assertNull(LogSanitizer.redact(null));
    }

    @Test
    void describeShowsClassNamesOnlyAndNeverMessages() {
        Exception e = new IllegalStateException("secret https://x/y?X-Amz-Signature=1", new java.io.IOException("chat-media/1/" + UUID + ".jpg"));
        String d = LogSanitizer.describe(e);
        assertEquals("IllegalStateException<-IOException", d);
    }

    @Test
    void detectsTimeoutsAnywhereInTheCauseChain() {
        assertTrue(LogSanitizer.isTimeout(new RuntimeException(new java.net.SocketTimeoutException("read timed out"))));
        assertFalse(LogSanitizer.isTimeout(new RuntimeException(new java.io.IOException("reset"))));
    }

    @Test
    void operationIdsAreShortAndVary() {
        String a = LogSanitizer.opId();
        assertEquals(8, a.length());
    }
}
