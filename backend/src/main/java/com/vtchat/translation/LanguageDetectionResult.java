package com.vtchat.translation;

/**
 * @param language   ISO code ("ta", "en"...), or null when it could not be determined
 * @param confidence 0..1 when the source of the answer reports one, otherwise null
 * @param script     writing system; null when the provider result carries none (provider-only detection)
 * @param romanized  true when a language that has its own script is written in Latin letters (e.g. Thanglish)
 */
public record LanguageDetectionResult(String language, Double confidence, ScriptType script, boolean romanized) {

    public static LanguageDetectionResult unknown(ScriptType script) {
        return new LanguageDetectionResult(null, null, script, false);
    }
}
