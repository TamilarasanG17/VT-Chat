package com.vtchat.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.vtchat.entity.Message;
import com.vtchat.entity.MessageType;
import com.vtchat.entity.User;
import com.vtchat.exception.ApiException;
import com.vtchat.repository.MessageDeletionRepository;
import com.vtchat.repository.MessageRepository;
import com.vtchat.security.JwtService;
import com.vtchat.service.MediaFileInspector.Detected;
import com.vtchat.service.MediaService.Variant;
import com.vtchat.storage.StorageException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import java.io.InputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    private static final String KEY = "chat-media/12/3f2c1a9e-7b4d-4c1e-9a55-0123456789ab.pdf";
    private static final Long ME = 2L, OTHER = 1L, ID = 5L;

    @Mock MediaFileInspector inspector;
    @Mock MediaStorageService storage;
    @Mock ImageThumbnailer thumbnailer;
    @Mock MessageService messageService;
    @Mock MessageRepository messages;
    @Mock MessageDeletionRepository deletions;
    @Mock JwtService jwtService;
    @Mock MediaDownloadService downloadService;
    @InjectMocks MediaService service;

    private ListAppender<ILoggingEvent> logs;
    private Logger root;

    @BeforeEach
    void captureLogs() {
        logs = new ListAppender<>();
        logs.start();
        root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        root.addAppender(logs);
        root.setLevel(Level.DEBUG);
    }

    @AfterEach
    void detach() {
        root.detachAppender(logs);
    }

    private Message message(MessageType type, Long senderId) {
        return Message.builder().id(ID).sender(User.builder().id(senderId).build()).messageType(type)
                .mediaKey(KEY).mediaName("a.pdf").mimeType("application/pdf").build();
    }

    private void visibleTo(Message m) {
        when(messageService.requireMessage(ID, ME)).thenReturn(m);
    }

    @Test
    void nonMembersAreRejectedBeforeAnyStorageOrSigningCall() {
        when(messageService.requireMessage(ID, ME)).thenThrow(ApiException.forbidden("FORBIDDEN", "no"));
        ApiException e = assertThrows(ApiException.class, () -> service.serve(ME, ID, Variant.INLINE));
        assertEquals(HttpStatus.NOT_FOUND, e.getStatus());
        verify(storage, never()).presignGet(any(), any());
        verify(storage, never()).presignable(any());
        verify(storage, never()).retrieve(any());
    }

    @Test
    void hiddenMessagesAreNotServed() {
        visibleTo(message(MessageType.IMAGE, OTHER));
        when(deletions.isHidden(ID, ME)).thenReturn(true);
        assertThrows(ApiException.class, () -> service.serve(ME, ID, Variant.INLINE));
        verify(storage, never()).presignGet(any(), any());
    }

    @Test
    void inlineViewAndThumbnailSignWithoutAnExistenceCheck() {
        visibleTo(message(MessageType.IMAGE, OTHER));
        when(storage.presignable(KEY)).thenReturn(true);
        when(storage.presignGet(eq(KEY), any())).thenReturn(Optional.of("https://signed.example/x"));
        when(storage.presignThumbnailGet(KEY)).thenReturn(Optional.of("https://signed.example/t"));
        assertEquals("https://signed.example/x", service.serve(ME, ID, Variant.INLINE).redirectUrl());
        assertEquals("https://signed.example/x", service.serve(ME, ID, Variant.VIEW).redirectUrl());
        assertEquals("https://signed.example/t", service.serve(ME, ID, Variant.THUMBNAIL).redirectUrl());
        verify(storage, never()).exists(any());
        verify(storage, never()).head(any());
        verify(storage, never()).thumbnailExists(any());
        verify(storage, never()).retrieve(any());
    }

    @Test
    void accessDoesNotCheckForAThumbnailOnObjectStorage() {
        visibleTo(message(MessageType.IMAGE, OTHER));
        when(jwtService.generateMediaToken(ME, ID)).thenReturn("TOKEN");
        when(storage.presignable(KEY)).thenReturn(true);
        var access = service.access(ME, ID);
        assertNotNull(access.thumbnailUrl());
        verify(storage, never()).thumbnailExists(any());
        verify(storage, never()).head(any());
    }

    @Test
    void localFilesAreReadOnceAndStreamed() {
        visibleTo(message(MessageType.IMAGE, OTHER));
        when(storage.presignable(KEY)).thenReturn(false);
        when(storage.retrieve(KEY)).thenReturn(Optional.of(new org.springframework.core.io.ByteArrayResource(new byte[]{1})));
        var served = service.serve(ME, ID, Variant.INLINE);
        assertNull(served.redirectUrl());
        assertNotNull(served.resource());
        verify(storage, org.mockito.Mockito.times(1)).retrieve(KEY);
        verify(storage, never()).exists(any());
    }

    @Test
    void missingLocalFileIs404() {
        visibleTo(message(MessageType.IMAGE, OTHER));
        when(storage.presignable(KEY)).thenReturn(false);
        when(storage.retrieve(KEY)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.serve(ME, ID, Variant.INLINE)).getStatus());
    }

    @Test
    void receiverDownloadFetchesOnceAndRecordsWithoutAnExistenceCheck() {
        visibleTo(message(MessageType.FILE, OTHER));
        when(storage.presignable(KEY)).thenReturn(false);
        when(storage.retrieve(KEY)).thenReturn(Optional.of(new org.springframework.core.io.ByteArrayResource(new byte[]{1})));
        var served = service.serve(ME, ID, Variant.DOWNLOAD);
        assertNull(served.redirectUrl());
        assertNotNull(served.resource());
        verify(storage, org.mockito.Mockito.times(1)).retrieve(KEY);
        verify(storage, never()).exists(any());
        verify(storage, never()).presignGet(any(), any());
        verify(downloadService).record(ID, ME);
    }

    @Test
    void missingDocumentIsNotRecordedAsDownloaded() {
        visibleTo(message(MessageType.FILE, OTHER));
        when(storage.presignable(KEY)).thenReturn(false);
        when(storage.retrieve(KEY)).thenReturn(Optional.empty());
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.serve(ME, ID, Variant.DOWNLOAD)).getStatus());
        verify(downloadService, never()).record(anyLong(), anyLong());
    }

    @Test
    void downloadLinkForAStreamedDocumentChecksMetadataOnceAndNeverDownloads() {
        visibleTo(message(MessageType.FILE, OTHER));
        when(storage.presignable(KEY)).thenReturn(false);
        when(storage.exists(KEY)).thenReturn(true);
        when(jwtService.generateMediaToken(ME, ID)).thenReturn("TOKEN");
        var link = service.downloadLink(ME, ID);
        assertFalse(link.absolute());
        verify(storage, org.mockito.Mockito.times(1)).exists(KEY);
        verify(storage, never()).retrieve(any());
    }

    @Test
    void downloadLinkForAMissingDocumentIs404() {
        visibleTo(message(MessageType.FILE, OTHER));
        when(storage.presignable(KEY)).thenReturn(false);
        when(storage.exists(KEY)).thenReturn(false);
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(ApiException.class, () -> service.downloadLink(ME, ID)).getStatus());
    }

    @Test
    void senderCannotDownloadTheirOwnDocument() {
        visibleTo(message(MessageType.FILE, ME));
        assertEquals(HttpStatus.FORBIDDEN, assertThrows(ApiException.class, () -> service.serve(ME, ID, Variant.DOWNLOAD)).getStatus());
        verify(storage, never()).presignGet(any(), any());
        assertThrows(ApiException.class, () -> service.downloadLink(ME, ID));
    }

    @Test
    void timeoutIs504AndOutageIs503AndNeitherLogsTheKey() {
        visibleTo(message(MessageType.IMAGE, OTHER));
        when(storage.presignable(KEY)).thenReturn(true);
        when(storage.presignGet(eq(KEY), any())).thenThrow(new StorageException(StorageException.Kind.TIMEOUT, "link"));
        ApiException timeout = assertThrows(ApiException.class, () -> service.serve(ME, ID, Variant.INLINE));
        assertEquals(HttpStatus.GATEWAY_TIMEOUT, timeout.getStatus());
        when(storage.presignGet(eq(KEY), any())).thenThrow(new StorageException(StorageException.Kind.UNAVAILABLE, "link"));
        ApiException outage = assertThrows(ApiException.class, () -> service.serve(ME, ID, Variant.INLINE));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, outage.getStatus());
        assertFalse(outage.getMessage().contains("chat-media"));
        for (ILoggingEvent e : logs.list) {
            assertFalse(e.getFormattedMessage().contains("chat-media"), e.getFormattedMessage());
            assertFalse(e.getFormattedMessage().contains(KEY), e.getFormattedMessage());
        }
    }

    private MockMultipartFile pdf() {
        return new MockMultipartFile("file", "a.pdf", "application/pdf", new byte[]{1, 2, 3});
    }

    private Detected detected() {
        return new Detected(MessageType.FILE, "pdf", "application/pdf");
    }

    @Test
    void failedUploadCreatesNoMessage() throws Exception {
        when(storage.store(any(), any(InputStream.class))).thenThrow(new StorageException(StorageException.Kind.UNAVAILABLE, "upload"));
        ApiException e = assertThrows(ApiException.class, () -> service.storeAndCreate(ME, 12L, pdf(), detected(), "a.pdf", null, null, null));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, e.getStatus());
        verify(messageService, never()).sendMedia(any(), any(), any(), any(), any());
        verify(storage, never()).delete(any());
    }

    @Test
    void databaseFailureAfterUploadRemovesTheNewObject() throws Exception {
        when(storage.store(any(), any(InputStream.class))).thenReturn(KEY);
        when(messageService.sendMedia(any(), any(), any(), any(), any())).thenThrow(new IllegalStateException("db down"));
        assertThrows(IllegalStateException.class, () -> service.storeAndCreate(ME, 12L, pdf(), detected(), "a.pdf", null, null, null));
        verify(storage).delete(KEY);
    }

    @Test
    void sharedObjectIsKeptWhileAnotherLiveMessageUsesIt() {
        when(messages.findById(ID)).thenReturn(Optional.of(message(MessageType.FILE, OTHER)));
        when(messages.countByMediaKeyAndDeletedFalse(KEY)).thenReturn(1L);
        service.cleanupIfUnreferenced(ID);
        verify(storage, never()).delete(any());
        when(messages.countByMediaKeyAndDeletedFalse(KEY)).thenReturn(0L);
        service.cleanupIfUnreferenced(ID);
        verify(storage).delete(KEY);
    }
}
