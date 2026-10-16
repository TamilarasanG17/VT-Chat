package com.vtchat.controller;

import com.vtchat.dto.ApiResponse;
import com.vtchat.dto.message.MediaUploadResponse;
import com.vtchat.dto.message.MessageMediaResponse;
import com.vtchat.entity.Message;
import com.vtchat.entity.MessageType;
import com.vtchat.exception.ApiException;
import com.vtchat.security.AuthenticatedUser;
import com.vtchat.service.MediaService;
import com.vtchat.service.MessageActionService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

@RestController
@RequiredArgsConstructor
public class MediaController {

    private final MessageActionService actions;
    private final MediaService mediaService;

    @PostMapping(value = "/api/conversations/{conversationId}/media", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MediaUploadResponse>> uploadMedia(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long conversationId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(value = "caption", required = false) String caption,
            @RequestParam(value = "replyToMessageId", required = false) Long replyToMessageId) {
        var message = actions.sendMedia(principal.id(), conversationId, file, caption, replyToMessageId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("File sent", new MediaUploadResponse(message)));
    }

    @PostMapping(value = "/api/conversations/{conversationId}/voice", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<MediaUploadResponse>> uploadVoice(
            @AuthenticationPrincipal AuthenticatedUser principal,
            @PathVariable Long conversationId,
            @RequestPart("file") MultipartFile file,
            @RequestParam("duration") Double duration,
            @RequestParam(value = "replyToMessageId", required = false) Long replyToMessageId) {
        var message = actions.sendVoice(principal.id(), conversationId, file, duration, replyToMessageId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.ok("Voice message sent", new MediaUploadResponse(message)));
    }

    @GetMapping("/api/media/{messageId}/access")
    public ResponseEntity<ApiResponse<MessageMediaResponse>> access(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                    @PathVariable Long messageId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", mediaService.access(principal.id(), messageId)));
    }

    @GetMapping("/api/media/{messageId}")
    public ResponseEntity<Resource> inline(@AuthenticationPrincipal AuthenticatedUser principal,
                                           @PathVariable Long messageId) {
        var served = mediaService.serve(principal.id(), messageId, MediaService.Variant.INLINE);
        if (served.redirectUrl() != null) return redirect(served.redirectUrl());
        boolean inline = served.message().getMessageType() != MessageType.FILE;
        return serve(served.message(), served.resource(), served.message().getMimeType(), inline);
    }

    @GetMapping("/api/media/{messageId}/view")
    public ResponseEntity<Resource> viewInBrowser(@AuthenticationPrincipal AuthenticatedUser principal,
                                                  @PathVariable Long messageId) {
        var served = mediaService.serve(principal.id(), messageId, MediaService.Variant.VIEW);
        if (!mediaService.isInlineViewable(served.message())) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "NOT_VIEWABLE", "This file type cannot be opened in the browser");
        }
        if (served.redirectUrl() != null) return redirect(served.redirectUrl());
        if (served.message().getMessageType() == MessageType.FILE) {
            return servePdf(served.message(), served.resource());
        }
        return serve(served.message(), served.resource(), served.message().getMimeType(), true);
    }

    @GetMapping("/api/media/{messageId}/download")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal AuthenticatedUser principal,
                                             @PathVariable Long messageId) {
        var served = mediaService.serve(principal.id(), messageId, MediaService.Variant.DOWNLOAD);
        if (served.redirectUrl() != null) return redirect(served.redirectUrl());
        return serve(served.message(), served.resource(), served.message().getMimeType(), false);
    }

    @GetMapping("/api/media/{messageId}/download-url")
    public ResponseEntity<ApiResponse<MediaService.DownloadLink>> downloadUrl(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                              @PathVariable Long messageId) {
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .body(ApiResponse.ok("OK", mediaService.downloadLink(principal.id(), messageId)));
    }

    @GetMapping("/api/media/{messageId}/thumbnail")
    public ResponseEntity<Resource> thumbnail(@AuthenticationPrincipal AuthenticatedUser principal,
                                              @PathVariable Long messageId) {
        var served = mediaService.serve(principal.id(), messageId, MediaService.Variant.THUMBNAIL);
        if (served.redirectUrl() != null) return redirect(served.redirectUrl());
        return serve(served.message(), served.resource(), "image/jpeg", true);
    }

    private static ResponseEntity<Resource> redirect(String url) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, url)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header("Referrer-Policy", "no-referrer")
                .build();
    }

    private static ResponseEntity<Resource> servePdf(Message message, Resource resource) {
        String name = message.getMediaName() == null ? "file.pdf" : message.getMediaName();
        ContentDisposition disposition = ContentDisposition.inline().filename(name, StandardCharsets.UTF_8).build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                .body(resource);
    }

    private static ResponseEntity<Resource> serve(Message message, Resource resource, String mime, boolean inline) {
        String name = message.getMediaName() == null ? "file" : message.getMediaName();
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(name, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(mime))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "default-src 'none'; sandbox")
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate())
                .body(resource);
    }
}
