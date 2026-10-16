package com.vtchat.service;

import com.vtchat.dto.translation.LanguageResponse;
import com.vtchat.dto.translation.TranslationResponse;

import java.util.List;

public interface TranslationService {

    TranslationResponse translateMessage(Long userId, Long messageId, String targetLanguage);

    List<LanguageResponse> supportedLanguages();
}
