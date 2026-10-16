package com.vtchat.dto.translation;

public record TranslationResponse(Long messageId, String sourceLanguage, String sourceScript, boolean romanized,
                                  String targetLanguage, String originalText, String translatedText) {
}
