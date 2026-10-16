package com.vtchat.controller;

import com.vtchat.dto.ApiResponse;
import com.vtchat.dto.ai.KnowledgeDtos.AccessResponse;
import com.vtchat.dto.ai.KnowledgeDtos.DocumentResponse;
import com.vtchat.exception.ApiException;
import com.vtchat.security.AuthenticatedUser;
import com.vtchat.service.KnowledgeFileInspector;
import com.vtchat.service.KnowledgeService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai/knowledge/documents")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService service;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentResponse>> upload(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                @RequestPart("file") MultipartFile file) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok("Uploaded", service.upload(principal.id(), file)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DocumentResponse>>> list(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok("OK", service.list(principal.id())));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<DocumentResponse>> get(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("OK", service.get(principal.id(), id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Map<String, Object>>> delete(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        service.delete(principal.id(), id);
        return ResponseEntity.ok(ApiResponse.ok("Deleted"));
    }

    @PostMapping("/{id}/reprocess")
    public ResponseEntity<ApiResponse<DocumentResponse>> reprocess(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        return ResponseEntity.accepted().body(ApiResponse.ok("Processing started", service.reprocess(principal.id(), id)));
    }

    @GetMapping("/{id}/access")
    public ResponseEntity<ApiResponse<AccessResponse>> access(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.ok("OK", service.access(principal.id(), id)));
    }

    @GetMapping("/{id}/view")
    public ResponseEntity<Resource> view(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        var loaded = service.load(principal.id(), id);
        String type = loaded.document().getFileType();
        if (!KnowledgeFileInspector.viewable(type)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "NOT_VIEWABLE", "This file type cannot be opened in the browser");
        }
        return serve(loaded.document().getFileName(), loaded.resource(), type, true);
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> download(@AuthenticationPrincipal AuthenticatedUser principal, @PathVariable Long id) {
        var loaded = service.load(principal.id(), id);
        return serve(loaded.document().getFileName(), loaded.resource(), loaded.document().getFileType(), false);
    }

    private static ResponseEntity<Resource> serve(String name, Resource resource, String type, boolean inline) {
        ContentDisposition disposition = (inline ? ContentDisposition.inline() : ContentDisposition.attachment())
                .filename(name, StandardCharsets.UTF_8).build();
        ResponseEntity.BodyBuilder b = ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(KnowledgeFileInspector.mimeFor(type)))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePrivate());
        if (!"pdf".equals(type) || !inline) {
            b.header("Content-Security-Policy", "default-src 'none'; sandbox");
        }
        return b.body(resource);
    }
}
