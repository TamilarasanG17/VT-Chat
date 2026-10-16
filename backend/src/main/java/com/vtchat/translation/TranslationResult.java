package com.vtchat.translation;

/** What a provider returns for one translation. {@code detectedSourceLanguage} is null if the provider did not report it. */
public record TranslationResult(String translatedText, String detectedSourceLanguage) {
}
