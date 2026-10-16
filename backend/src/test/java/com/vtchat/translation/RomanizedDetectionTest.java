package com.vtchat.translation;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Pure unit tests (no Spring, no network) for script, Romanized-Tamil detection and normalization. */
class RomanizedDetectionTest {

    private final ScriptDetector scripts = new ScriptDetector();
    private final RomanizedLanguageDetector romanized = new RomanizedLanguageDetector();
    private final TamilTransliterator transliterator = new TamilTransliterator();

    @Test
    void thanglishIsDetectedAsRomanizedTamil() {
        for (String text : new String[]{"epdi iruka", "saptiya", "enna panra", "enga pora", "romba nalla iruku",
                "naan office ku poren", "meeting mudinjacha?", "lunch saptiya?", "today romba busy"}) {
            var found = romanized.detect(text, null, null);
            assertTrue(found.isPresent(), text);
            assertEquals("ta", found.get().language(), text);
            assertEquals(ScriptType.LATIN, scripts.detect(text), text);
        }
    }

    @Test
    void plainEnglishIsNotRomanized() {
        for (String text : new String[]{"How are you?", "I need to go to the office today", "See you tomorrow, then we can talk about the plan", "ok", "hello"}) {
            assertFalse(romanized.detect(text, "en", 0.99).isPresent(), text);
        }
    }

    @Test
    void confidentEnglishProviderNeedsMoreEvidence() {
        // one weak Tamil word inside a long English sentence must not flip the language
        assertFalse(romanized.detect("I told him that the ku was wrong in this long sentence", "en", 0.95).isPresent());
    }

    @Test
    void tamilScriptAndOtherScripts() {
        assertEquals(ScriptType.TAMIL, scripts.detect("எப்படி இருக்க?"));
        assertEquals("ta", scripts.languageFor(ScriptType.TAMIL));
        assertEquals(ScriptType.DEVANAGARI, scripts.detect("आप कैसे हैं"));
        assertEquals(ScriptType.JAPANESE, scripts.detect("お元気ですか"));
        assertEquals(ScriptType.UNKNOWN, scripts.detect("12345 😀"));
    }

    @Test
    void thanglishIsNormalizedToTamilScriptAndEnglishWordsStay() {
        assertEquals("எப்படி இருக்கா", transliterator.transliterate("epdi iruka"));
        assertEquals("நான் office க்கு போறேன்", transliterator.transliterate("naan office ku poren"));
        assertEquals("lunch சாப்டியா?", transliterator.transliterate("lunch saptiya?"));
        assertEquals("வந்தேன்", transliterator.transliterate("vandhen"));
    }
}
