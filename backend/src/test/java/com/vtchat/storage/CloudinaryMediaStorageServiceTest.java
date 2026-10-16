package com.vtchat.storage;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.cloudinary.Api;
import com.cloudinary.Cloudinary;
import com.cloudinary.Uploader;
import com.cloudinary.utils.ObjectUtils;
import com.vtchat.config.MediaStorageProperties;
import com.vtchat.service.MediaStorageService.Download;
import com.vtchat.service.MediaStorageService.StoreRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CloudinaryMediaStorageServiceTest {

    private static final String SECRET = "TestApiSecretValue1234567890x";
    private static final String UUID = "3f2c1a9e-7b4d-4c1e-9a55-0123456789ab";
    private static final String IMAGE = "cld:ia:vtchat/chat-media/12/" + UUID + ".jpg";
    private static final String VIDEO = "cld:va:vtchat/chat-media/12/" + UUID + ".mp4";
    private static final String PDF = "cld:ra:vtchat/chat-media/12/" + UUID + ".pdf";
    private static final String AVATAR = "cld:iu:vtchat/avatars/7/" + UUID + ".jpg";

    private Cloudinary cloudinary;
    private Uploader uploader;
    private Api api;
    private final List<String[]> orphans = new ArrayList<>();
    private CloudinaryMediaStorageService storage;
    private ListAppender<ILoggingEvent> logs;
    private Logger logger;

    @BeforeEach
    void setUp() {
        MediaStorageProperties.Cloudinary cfg = new MediaStorageProperties.Cloudinary();
        cfg.setCloudName("democloud");
        cfg.setApiKey("123456789012345");
        cfg.setApiSecret(SECRET);
        cfg.setFolder("vtchat");
        cloudinary = spy(new Cloudinary(ObjectUtils.asMap("cloud_name", "democloud", "api_key", "123456789012345", "api_secret", SECRET, "secure", true)));
        uploader = mock(Uploader.class);
        api = mock(Api.class);
        doReturn(uploader).when(cloudinary).uploader();
        doReturn(api).when(cloudinary).api();
        storage = new CloudinaryMediaStorageService(cloudinary, cfg, (provider, key, category) -> orphans.add(new String[]{provider, key, category}));
        logger = (Logger) LoggerFactory.getLogger(CloudinaryMediaStorageService.class);
        logs = new ListAppender<>();
        logs.start();
        logger.addAppender(logs);
        logger.setLevel(Level.DEBUG);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(logs);
        storage.close();
    }

    @Test
    void privateImageAndVideoUrlsAreSignedAuthenticatedAndNeverContainTheSecret() {
        String image = storage.presignGet(IMAGE, new Download("image/jpeg", "photo.jpg", true)).orElseThrow();
        assertTrue(image.startsWith("https://res.cloudinary.com/democloud/image/authenticated/s--"), image);
        assertTrue(image.endsWith("vtchat/chat-media/12/" + UUID + ".jpg"), image);
        String video = storage.presignGet(VIDEO, new Download("video/mp4", "clip.mp4", true)).orElseThrow();
        assertTrue(video.contains("/video/authenticated/s--") && video.endsWith(".mp4"), video);
        assertFalse(image.contains(SECRET) || video.contains(SECRET));
        assertFalse(image.contains("123456789012345"), "the API key is not part of a delivery URL");
    }

    @Test
    void downloadsAddTheAttachmentFlagWithASafeAsciiName() {
        String url = storage.presignGet(IMAGE, new Download("image/jpeg", "my holiday photo.jpg", false)).orElseThrow();
        assertTrue(url.contains("fl_attachment:my_holiday_photo"), url);
        String tamil = storage.presignGet(IMAGE, new Download("image/jpeg", "புகைப்படம்.jpg", false)).orElseThrow();
        assertTrue(tamil.contains("fl_attachment:file"), tamil);
        assertEquals("my_report", CloudinaryMediaStorageService.attachmentName("my report.final.pdf".replace(".final", "")));
        assertEquals("file", CloudinaryMediaStorageService.attachmentName("../../"));
    }

    @Test
    void thumbnailsAreDerivedOnDeliveryNotStored() throws Exception {
        String url = storage.presignThumbnailGet(IMAGE).orElseThrow();
        assertTrue(url.contains("c_limit") && url.contains("h_480") && url.contains("w_480"), url);
        storage.storeThumbnail(IMAGE, new byte[]{1});
        verify(uploader, never()).upload(any(), anyMap());
        assertTrue(storage.thumbnailExists(IMAGE));
        assertFalse(storage.thumbnailExists(VIDEO));
        assertTrue(storage.presignThumbnailGet(VIDEO).isEmpty());
        assertEquals(0, storage.requests("resource"), "no remote existence check for thumbnails");
    }

    @Test
    void documentsAreNeverHandedToTheBrowserAsAUrl() {
        assertFalse(storage.presignable(PDF));
        assertTrue(storage.presignGet(PDF, new Download("application/pdf", "a.pdf", true)).isEmpty());
        assertTrue(storage.presignable(IMAGE) && storage.presignable(VIDEO));
        assertFalse(storage.presignable("chat-media/1/" + UUID + ".jpg"), "local keys are not Cloudinary references");
        assertFalse(storage.presignable("cld:ia:../../x"));
    }

    @Test
    void onlyAvatarsHaveAPublicUrlAndItIsUnsigned() {
        String url = storage.publicUrl(AVATAR).orElseThrow();
        assertTrue(url.startsWith("https://res.cloudinary.com/democloud/image/upload/"), url);
        assertFalse(url.contains("s--"));
        assertTrue(storage.publicUrl(IMAGE).isEmpty(), "private chat media never has a public URL");
        assertTrue(storage.publicUrl(PDF).isEmpty());
        assertTrue(storage.publicUrl("avatars/7/" + UUID + ".jpg").isEmpty());
    }

    @Test
    void generatingUrlsMakesNoProviderRequest() {
        storage.presignGet(IMAGE, new Download("image/jpeg", "a.jpg", true));
        storage.presignThumbnailGet(IMAGE);
        storage.publicUrl(AVATAR);
        for (String op : new String[]{"upload", "delete", "resource", "ping", "fetch"}) {
            assertEquals(0, storage.requests(op), op);
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureUpload(StoreRequest request, byte[] bytes, String returnedFormat) throws Exception {
        ArgumentCaptor<Map<String, Object>> options = ArgumentCaptor.forClass(Map.class);
        when(uploader.upload(any(File.class), anyMap())).thenAnswer(inv -> {
            Map<String, Object> o = inv.getArgument(1);
            String pid = (String) o.get("public_id");
            return ObjectUtils.asMap("public_id", pid, "resource_type", o.get("resource_type"), "type", o.get("type"),
                    "format", returnedFormat, "bytes", bytes.length);
        });
        storage.store(request, new ByteArrayInputStream(bytes));
        verify(uploader).upload(any(File.class), options.capture());
        return options.getValue();
    }

    @Test
    void usesTheRightResourceTypeAndDeliveryTypePerCategory() throws Exception {
        byte[] data = new byte[]{1, 2, 3};
        Map<String, Object> image = captureUpload(StoreRequest.chat(12, "jpg", "image/jpeg", 3), data, "jpg");
        assertEquals("image", image.get("resource_type"));
        assertEquals("authenticated", image.get("type"));
        assertTrue(((String) image.get("public_id")).matches("vtchat/chat-media/12/[0-9a-f-]{36}"), String.valueOf(image.get("public_id")));
        assertEquals(false, image.get("overwrite"));
    }

    @Test
    void voiceAndVideoAreVideoPdfAndDocumentsAreRawAvatarsArePublicImages() throws Exception {
        byte[] data = new byte[]{1, 2, 3};
        for (Object[] c : new Object[][]{
                {StoreRequest.chat(1, "webm", "audio/webm", 3), "video", "authenticated"},
                {StoreRequest.chat(1, "mp4", "video/mp4", 3), "video", "authenticated"},
                {StoreRequest.chat(1, "pdf", "application/pdf", 3), "raw", "authenticated"},
                {StoreRequest.chat(1, "docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", 3), "raw", "authenticated"},
                {StoreRequest.knowledge(4, "md", "text/markdown", 3), "raw", "authenticated"},
                {StoreRequest.avatar(7, "jpg", "image/jpeg", 3), "image", "upload"}}) {
            org.mockito.Mockito.clearInvocations(uploader);
            StoreRequest request = (StoreRequest) c[0];
            String format = "raw".equals(c[1]) ? null : request.extension();
            Map<String, Object> options = captureUpload(request, data, format);
            assertEquals(c[1], options.get("resource_type"), request.contentType());
            assertEquals(c[2], options.get("type"), request.contentType());
            boolean raw = "raw".equals(c[1]);
            assertEquals(raw, ((String) options.get("public_id")).endsWith("." + request.extension()), "raw public IDs keep the extension");
        }
    }

    @Test
    void returnsAValidatedReferenceAndNeverTheUrl() throws Exception {
        when(uploader.upload(any(File.class), anyMap())).thenAnswer(inv -> {
            Map<String, Object> o = inv.getArgument(1);
            return ObjectUtils.asMap("public_id", o.get("public_id"), "resource_type", "image", "type", "authenticated", "format", "jpg", "bytes", 3,
                    "secure_url", "https://res.cloudinary.com/democloud/image/authenticated/v1/x.jpg");
        });
        String key = storage.store(StoreRequest.chat(12, "jpg", "image/jpeg", 3), new ByteArrayInputStream(new byte[]{1, 2, 3}));
        assertTrue(key.startsWith("cld:ia:vtchat/chat-media/12/") && key.endsWith(".jpg"), key);
        assertFalse(key.contains("http"));
        assertTrue(CloudinaryRef.parse(key).isPresent());
    }

    @Test
    void aSizeMismatchIsNotAcceptedAndTheAssetIsRemoved() throws Exception {
        when(uploader.upload(any(File.class), anyMap())).thenAnswer(inv -> {
            Map<String, Object> o = inv.getArgument(1);
            return ObjectUtils.asMap("public_id", o.get("public_id"), "resource_type", "image", "type", "authenticated", "format", "jpg", "bytes", 999);
        });
        when(uploader.destroy(anyString(), anyMap())).thenReturn(ObjectUtils.asMap("result", "ok"));
        StorageException e = assertThrows(StorageException.class,
                () -> storage.store(StoreRequest.chat(12, "jpg", "image/jpeg", 3), new ByteArrayInputStream(new byte[]{1, 2, 3})));
        assertEquals(StorageException.Kind.FAILED, e.kind());
        verify(uploader).destroy(anyString(), anyMap());
        assertTrue(orphans.isEmpty());
    }

    @Test
    void aTruncatedInputIsRejectedBeforeAnythingIsSent() throws Exception {
        assertThrows(IOException.class, () -> storage.store(StoreRequest.chat(12, "jpg", "image/jpeg", 10), new ByteArrayInputStream(new byte[]{1, 2, 3})));
        verify(uploader, never()).upload(any(), anyMap());
    }

    @Test
    void failedUploadsAreCategorisedWithoutLeakingSdkText() throws Exception {
        String leaked = "Invalid Signature for https://api.cloudinary.com/v1_1/democloud/image/upload api_secret=" + SECRET;
        when(uploader.upload(any(File.class), anyMap())).thenThrow(new RuntimeException(leaked));
        StorageException e = assertThrows(StorageException.class,
                () -> storage.store(StoreRequest.chat(12, "jpg", "image/jpeg", 3), new ByteArrayInputStream(new byte[]{1, 2, 3})));
        assertEquals(StorageException.Kind.ACCESS_DENIED, e.kind());
        assertNull(e.getCause());
        assertFalse(String.valueOf(e.getMessage()).contains(SECRET) || e.getMessage().contains("http"));
        for (ILoggingEvent event : logs.list) {
            String text = event.getFormattedMessage() + String.valueOf(event.getThrowableProxy());
            assertFalse(text.contains(SECRET) || text.contains("cloudinary.com") || text.contains("vtchat/chat-media"), text);
        }
    }

    private static void assertNull(Object o) {
        org.junit.jupiter.api.Assertions.assertNull(o);
    }

    @Test
    void deleteUsesTheCorrectPublicIdResourceTypeAndDeliveryType() throws Exception {
        when(uploader.destroy(anyString(), anyMap())).thenReturn(ObjectUtils.asMap("result", "ok"));
        assertTrue(storage.delete(PDF));
        assertTrue(storage.delete(VIDEO));
        verify(uploader).destroy(eq("vtchat/chat-media/12/" + UUID + ".pdf"), org.mockito.ArgumentMatchers.argThat(m ->
                "raw".equals(m.get("resource_type")) && "authenticated".equals(m.get("type")) && Boolean.TRUE.equals(m.get("invalidate"))));
        verify(uploader).destroy(eq("vtchat/chat-media/12/" + UUID), org.mockito.ArgumentMatchers.argThat(m -> "video".equals(m.get("resource_type"))));
        assertTrue(orphans.isEmpty());
    }

    @Test
    void aMissingAssetCountsAsDeleted() throws Exception {
        when(uploader.destroy(anyString(), anyMap())).thenReturn(ObjectUtils.asMap("result", "not found"));
        assertTrue(storage.delete(IMAGE));
        assertTrue(orphans.isEmpty());
    }

    @Test
    void failedDeletesAreReportedAsFailedAndRecordedForRecoveryNeverAsSuccess() throws Exception {
        when(uploader.destroy(anyString(), anyMap())).thenThrow(new RuntimeException("Read timed out"));
        assertFalse(storage.delete(IMAGE));
        assertEquals(1, orphans.size());
        assertEquals("cloudinary", orphans.get(0)[0]);
        assertEquals(IMAGE, orphans.get(0)[1]);
        assertEquals("TIMEOUT", orphans.get(0)[2]);
        for (ILoggingEvent event : logs.list) {
            assertFalse(event.getFormattedMessage().contains(UUID), event.getFormattedMessage());
        }
        when(uploader.destroy(anyString(), anyMap())).thenReturn(ObjectUtils.asMap("result", "error"));
        assertFalse(storage.delete(VIDEO));
        assertEquals("FAILED", orphans.get(1)[2]);
    }

    @Test
    void referencesThatAreNotCloudinaryOnesAreIgnoredByDelete() throws Exception {
        assertTrue(storage.delete("chat-media/1/" + UUID + ".jpg"));
        verify(uploader, never()).destroy(anyString(), anyMap());
    }

    @Test
    void missingAssetsAnswerEmptyAndOtherFailuresAreNotMistakenForMissing() throws Exception {
        when(api.resource(anyString(), anyMap())).thenThrow(new RuntimeException("Resource not found - x"));
        assertTrue(storage.head(IMAGE).isEmpty());
        when(api.resource(anyString(), anyMap())).thenThrow(new RuntimeException("Read timed out"));
        assertEquals(StorageException.Kind.TIMEOUT, assertThrows(StorageException.class, () -> storage.head(IMAGE)).kind());
        when(api.resource(anyString(), anyMap())).thenThrow(new RuntimeException("Invalid api_key 1234"));
        assertEquals(StorageException.Kind.ACCESS_DENIED, assertThrows(StorageException.class, () -> storage.head(IMAGE)).kind());
        assertTrue(storage.head("not-a-reference").isEmpty());
    }

    @Test
    void classifiesSdkAndTransportFailures() {
        class NotFound extends RuntimeException { }
        class AuthorizationRequired extends RuntimeException { }
        class RateLimited extends RuntimeException { }
        assertEquals(StorageException.Kind.NOT_FOUND, CloudinaryMediaStorageService.classify(new NotFound()));
        assertEquals(StorageException.Kind.ACCESS_DENIED, CloudinaryMediaStorageService.classify(new AuthorizationRequired()));
        assertEquals(StorageException.Kind.UNAVAILABLE, CloudinaryMediaStorageService.classify(new RateLimited()));
        assertEquals(StorageException.Kind.TIMEOUT, CloudinaryMediaStorageService.classify(new java.net.SocketTimeoutException("x")));
        assertEquals(StorageException.Kind.TIMEOUT, CloudinaryMediaStorageService.classify(new RuntimeException("wrapped", new java.net.http.HttpTimeoutException("x"))));
        assertEquals(StorageException.Kind.UNAVAILABLE, CloudinaryMediaStorageService.classify(new IOException("connection reset")));
        assertEquals(StorageException.Kind.ACCESS_DENIED, CloudinaryMediaStorageService.classify(new RuntimeException("Invalid cloud_name abc")));
        assertEquals(StorageException.Kind.FAILED, CloudinaryMediaStorageService.classify(new IllegalArgumentException("bad")));
    }

    @Test
    void nothingIsSentToTheProviderForTamperedReferences() throws Exception {
        assertTrue(storage.retrieve("cld:ra:../../etc/passwd").isEmpty());
        assertTrue(storage.head("cld:ia:other/folder").isEmpty());
        assertNotNull(storage);
        assertEquals(0, storage.requests("fetch"));
        assertEquals(0, storage.requests("resource"));
    }
}
