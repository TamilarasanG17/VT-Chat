package com.vtchat.service;

import com.vtchat.config.AppProperties;
import com.vtchat.exception.ApiException;
import com.vtchat.storage.StorageException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AvatarStorageServiceTest {

    private static final String UUID = "3f2c1a9e-7b4d-4c1e-9a55-0123456789ab";

    @TempDir Path tmp;
    private MediaStorageService storage;
    private AvatarStorageService service;

    @BeforeEach
    void setUp() {
        storage = mock(MediaStorageService.class);
        AppProperties props = new AppProperties();
        props.getStorage().setUploadDir(tmp.toString());
        service = new AvatarStorageService(props, storage);
        service.init();
    }

    private static byte[] image(String format, int size) throws Exception {
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, format, out);
        return out.toByteArray();
    }

    @Test
    void validImageIsReEncodedAndStoredUnderTheAvatarNamespace() throws Exception {
        when(storage.store(any(), any(InputStream.class))).thenReturn("avatars/7/" + UUID + ".jpg");
        String ref = service.store(new MockMultipartFile("file", "me.png", "image/png", image("png", 64)), 7L);
        assertEquals("avatars/7/" + UUID + ".jpg", ref);
        ArgumentCaptor<MediaStorageService.StoreRequest> req = ArgumentCaptor.forClass(MediaStorageService.StoreRequest.class);
        verify(storage).store(req.capture(), any(InputStream.class));
        assertEquals("avatars", req.getValue().area());
        assertEquals(7L, req.getValue().scope());
        assertEquals("jpg", req.getValue().extension());
        assertEquals("image/jpeg", req.getValue().contentType());
    }

    @Test
    void invalidOrWrongTypeImagesAreRejectedAndNothingIsUploaded() throws Exception {
        assertThrows(ApiException.class, () -> service.store(new MockMultipartFile("file", "x.png", "image/png", new byte[]{1, 2, 3}), 7L));
        assertThrows(ApiException.class, () -> service.store(new MockMultipartFile("file", "x.gif", "image/gif", image("gif", 64)), 7L));
        assertThrows(ApiException.class, () -> service.store(new MockMultipartFile("file", "x.png", "image/png", image("png", 8)), 7L));
        assertThrows(ApiException.class, () -> service.store(null, 7L));
        verify(storage, never()).store(any(), any(InputStream.class));
    }

    @Test
    void failedUploadSurfacesAsStorageExceptionWithoutCause() throws Exception {
        when(storage.store(any(), any(InputStream.class))).thenThrow(new StorageException(StorageException.Kind.TIMEOUT, "upload"));
        StorageException e = assertThrows(StorageException.class,
                () -> service.store(new MockMultipartFile("file", "me.png", "image/png", image("png", 64)), 7L));
        assertEquals(StorageException.Kind.TIMEOUT, e.kind());
    }

    @Test
    void newReferencesAreReadFromStorage() throws Exception {
        String key = "avatars/7/" + UUID + ".jpg";
        when(storage.retrieve(key)).thenReturn(Optional.of(new ByteArrayResource(new byte[]{9, 9})));
        assertEquals(2, service.load(key).orElseThrow().length);
    }

    @Test
    void missingAvatarIsEmptyAndOutageIsNotMissing() {
        String key = "avatars/7/" + UUID + ".jpg";
        when(storage.retrieve(key)).thenReturn(Optional.empty());
        assertTrue(service.load(key).isEmpty());
        when(storage.retrieve(key)).thenThrow(new StorageException(StorageException.Kind.UNAVAILABLE, "download"));
        assertThrows(StorageException.class, () -> service.load(key));
    }

    @Test
    void arbitraryReferencesAreNeverServed() {
        for (String bad : new String[]{"chat-media/1/" + UUID + ".jpg", "../secret", "images/" + UUID + ".png", "knowledge/1/" + UUID + ".pdf", "", null}) {
            assertTrue(service.load(bad).isEmpty(), String.valueOf(bad));
        }
        verify(storage, never()).retrieve(any());
    }

    @Test
    void legacyLocalAvatarsStillWorkAndAMissingOneIsEmpty() throws Exception {
        String name = UUID + ".jpg";
        assertTrue(AvatarStorageService.isLegacy(name));
        assertTrue(service.load(name).isEmpty());
        Files.write(tmp.resolve("avatars").resolve(name), new byte[]{1, 2, 3});
        assertEquals(3, service.load(name).orElseThrow().length);
        service.delete(name);
        assertFalse(Files.exists(tmp.resolve("avatars").resolve(name)));
        verify(storage, never()).delete(any());
    }

    @Test
    void deletingAStoredReferenceGoesToStorage() {
        service.delete("avatars/7/" + UUID + ".jpg");
        verify(storage).delete("avatars/7/" + UUID + ".jpg");
        service.delete("not-ours");
        service.delete(null);
        verify(storage, org.mockito.Mockito.times(1)).delete(any());
    }

    @Test
    void cloudinaryReferencesAreStoredReadDeletedAndExposedThroughTheStorageAbstraction() throws Exception {
        String ref = "cld:iu:vtchat/avatars/7/" + UUID + ".jpg";
        when(storage.store(any(), any(InputStream.class))).thenReturn(ref);
        assertEquals(ref, service.store(new MockMultipartFile("file", "me.png", "image/png", image("png", 64)), 7L));
        when(storage.retrieve(ref)).thenReturn(Optional.of(new ByteArrayResource(new byte[]{9, 9, 9})));
        assertEquals(3, service.load(ref).orElseThrow().length);
        when(storage.publicUrl(ref)).thenReturn(Optional.of("https://res.cloudinary.com/demo/image/upload/x.jpg"));
        assertTrue(service.publicUrl(ref).isPresent());
        assertTrue(service.publicUrl("avatars/7/" + UUID + ".jpg").isEmpty(), "local pictures have no public CDN URL");
        service.delete(ref);
        verify(storage).delete(ref);
    }

    @Test
    void localPicturesOfEitherLayoutCanBeReadForTheOptionalMigration() throws Exception {
        String name = UUID + ".jpg";
        Files.write(tmp.resolve("avatars").resolve(name), new byte[]{1, 2});
        assertEquals(2, service.readLocal(name).orElseThrow().length);
        String key = "avatars/7/" + UUID + ".jpg";
        when(storage.retrieve(key)).thenReturn(Optional.of(new ByteArrayResource(new byte[]{1, 2, 3, 4})));
        assertEquals(4, service.readLocal(key).orElseThrow().length);
        assertTrue(service.readLocal("cld:iu:vtchat/avatars/7/" + UUID + ".jpg").isEmpty());
    }
}
