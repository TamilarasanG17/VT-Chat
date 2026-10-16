package com.vtchat.config;

import com.vtchat.service.LocalMediaStorageService;
import com.vtchat.service.MediaStorageService;
import com.vtchat.storage.CloudinaryMediaStorageService;
import com.vtchat.storage.RoutingMediaStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MediaStorageConfigTest {

    @TempDir Path root;

    @SuppressWarnings("unchecked")
    private MediaStorageService pick(MediaStorageProperties props, MockEnvironment env, CloudinaryMediaStorageService cloud) {
        ObjectProvider<CloudinaryMediaStorageService> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(cloud);
        return new MediaStorageConfig().mediaStorageService(new LocalMediaStorageService(root), provider, props, env);
    }

    private static MediaStorageProperties props(String provider) {
        MediaStorageProperties p = new MediaStorageProperties();
        p.setProvider(provider);
        return p;
    }

    @Test
    void localNeedsNoCloudinaryCredentials() {
        MediaStorageService storage = pick(props("local"), new MockEnvironment(), null);
        assertTrue(storage instanceof LocalMediaStorageService);
    }

    @Test
    void productionRefusesLocalUnlessExplicitlyAllowed() {
        MockEnvironment prod = new MockEnvironment();
        prod.setActiveProfiles("prod");
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> pick(props("local"), prod, null));
        assertTrue(e.getMessage().contains("MEDIA_STORAGE_PROVIDER=cloudinary"));
        MediaStorageProperties allowed = props("local");
        allowed.setAllowLocalInProduction(true);
        assertTrue(pick(allowed, prod, null) instanceof LocalMediaStorageService);
    }

    @Test
    void cloudinaryUsesTheRoutingStorageSoOldLocalFilesStayReadable() {
        MediaStorageService storage = pick(props("cloudinary"), new MockEnvironment(), mock(CloudinaryMediaStorageService.class));
        assertTrue(storage instanceof RoutingMediaStorageService);
    }

    @Test
    void theRemovedR2ProviderAndTyposFailFastWithAClearMessage() {
        IllegalStateException r2 = assertThrows(IllegalStateException.class, () -> pick(props("r2"), new MockEnvironment(), null));
        assertTrue(r2.getMessage().contains("no longer supported"));
        assertThrows(IllegalStateException.class, () -> pick(props("clodinary"), new MockEnvironment(), null));
        assertThrows(IllegalStateException.class, () -> pick(props("cloudinary"), new MockEnvironment(), null));
    }

    @Test
    void missingCloudinarySettingsAreListedByNameNeverByValue() {
        MediaStorageProperties.Cloudinary c = new MediaStorageProperties.Cloudinary();
        List<String> problems = MediaStorageConfig.cloudinaryProblems(c);
        String text = String.join(";", problems);
        assertTrue(text.contains("CLOUDINARY_CLOUD_NAME") && text.contains("CLOUDINARY_API_KEY") && text.contains("CLOUDINARY_API_SECRET"), text);
        c.setCloudName("demo");
        c.setApiKey("123");
        c.setApiSecret("super-secret-value");
        assertTrue(MediaStorageConfig.cloudinaryProblems(c).isEmpty());
        c.setFolder("../escape");
        List<String> bad = MediaStorageConfig.cloudinaryProblems(c);
        assertEquals(1, bad.size());
        assertFalse(String.join(";", bad).contains("super-secret-value"));
        assertFalse(c.toString().contains("super-secret-value"), "toString is masked");
        assertSame(c, c);
    }
}
