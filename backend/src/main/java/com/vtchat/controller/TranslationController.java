package com.vtchat.controller;

import com.vtchat.dto.ApiResponse;
import com.vtchat.dto.translation.LanguageResponse;
import com.vtchat.dto.translation.TranslateMessageRequest;
import com.vtchat.dto.translation.TranslationResponse;
import com.vtchat.security.AuthenticatedUser;
import com.vtchat.service.TranslationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
public class TranslationController {

    private final TranslationService translationService;

    @PostMapping("/api/messages/{messageId}/translate")
    public ResponseEntity<ApiResponse<TranslationResponse>> translate(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                      @PathVariable Long messageId,
                                                                      @Valid @RequestBody TranslateMessageRequest request) {
        return ResponseEntity.ok(ApiResponse.ok("OK",
                translationService.translateMessage(principal.id(), messageId, request.targetLanguage())));
    }

    @GetMapping("/api/translation/languages")
    public ResponseEntity<ApiResponse<List<LanguageResponse>>> languages() {
        return ResponseEntity.ok(ApiResponse.ok("OK", translationService.supportedLanguages()));
    }
}
