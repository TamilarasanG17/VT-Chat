package com.vtchat.translation;

/**
 * The only thing the application knows about an external translation service. To switch provider, supply another
 * implementation of this interface; nothing else changes. Implementations must never put secrets, request bodies
 * or provider error text into exceptions (see {@link TranslationProviderException}).
 */
public interface TranslationProvider {

    /**
     * @param sourceLanguage ISO code, or null to let the provider detect it
     * @throws TranslationProviderException on any provider failure
     */
    TranslationResult translate(String text, String sourceLanguage, String targetLanguage);

    /** @throws TranslationProviderException on any provider failure */
    LanguageDetectionResult detectLanguage(String text);

    /** False when the required settings (e.g. the API key) are missing, so requests can fail fast and clearly. */
    default boolean isConfigured() {
        return true;
    }
}
