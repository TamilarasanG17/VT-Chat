package com.vtchat.service;

import com.vtchat.translation.LanguageDetectionResult;
import com.vtchat.translation.RomanizedLanguageDetector;
import com.vtchat.translation.RomanizedLanguageDetector.Detection;
import com.vtchat.translation.ScriptDetector;
import com.vtchat.translation.ScriptType;
import com.vtchat.translation.TranslationProvider;
import com.vtchat.translation.TranslationProviderException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Finds out what a text is written in, without asking the user:
 * <pre>
 *   1. script from the characters (Tamil, Devanagari, Latin...)         - local, free
 *   2. non-Latin script  -> language from the script (or provider for Devanagari / Arabic)
 *   3. Latin letters     -> Romanized-language check (is it Thanglish / Hinglish?)
 *                           decisive? done : ask the provider, then re-check with its answer
 * </pre>
 * The provider's opinion is never trusted blindly for Latin text: "epdi iruka" is reported as English or Indonesian
 * by general detectors. Provider failures during detection are not fatal here; the language simply stays unknown.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LanguageDetectionService {

    private final ScriptDetector scripts;
    private final RomanizedLanguageDetector romanized;
    private final TranslationProvider provider;

    public LanguageDetectionResult detect(String text) {
        ScriptType script = scripts.detect(text);
        if (script == ScriptType.UNKNOWN) {
            return LanguageDetectionResult.unknown(script);
        }

        if (script != ScriptType.LATIN) {
            String language = scripts.languageFor(script);
            if (language == null) {   // Devanagari (hi/mr), Arabic (ar/ur/fa), Cyrillic...: let the provider choose
                LanguageDetectionResult p = providerDetect(text);
                language = p != null ? p.language() : fallbackFor(script);
                return new LanguageDetectionResult(language, p != null ? p.confidence() : null, script, false);
            }
            return new LanguageDetectionResult(language, null, script, false);
        }

        Optional<Detection> local = romanized.detect(text, null, null);
        if (local.isPresent() && local.get().decisive()) {
            return romanizedResult(local.get());
        }
        LanguageDetectionResult p = providerDetect(text);
        Optional<Detection> decided = romanized.detect(text, p == null ? null : p.language(), p == null ? null : p.confidence());
        if (decided.isPresent()) {
            return romanizedResult(decided.get());
        }
        return new LanguageDetectionResult(p == null ? null : p.language(), p == null ? null : p.confidence(), ScriptType.LATIN, false);
    }

    /** Provider-only detection (no script or Romanized analysis). */
    public LanguageDetectionResult detectWithProvider(String text) {
        LanguageDetectionResult p = providerDetect(text);
        return p != null ? p : LanguageDetectionResult.unknown(null);
    }

    private static LanguageDetectionResult romanizedResult(Detection d) {
        return new LanguageDetectionResult(d.language(), Math.min(1.0, d.coverage()), ScriptType.LATIN, true);
    }

    private LanguageDetectionResult providerDetect(String text) {
        if (!provider.isConfigured()) {
            return null;
        }
        try {
            return provider.detectLanguage(text);
        } catch (TranslationProviderException e) {
            log.warn("Provider language detection failed ({})", e.getKind());
            return null;
        }
    }

    private static String fallbackFor(ScriptType script) {
        return switch (script) {
            case DEVANAGARI -> "hi";
            case ARABIC -> "ar";
            default -> null;
        };
    }
}
