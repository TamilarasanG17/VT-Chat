package com.vtchat.translation;

import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

/** Works out the writing system of a text from its Unicode characters. No network, no dictionary. */
@Component
public class ScriptDetector {

    /** A non-Latin script must make up at least this share of the letters to win over Latin ("meeting எப்போ"). */
    private static final double NON_LATIN_SHARE = 0.4;

    public ScriptType detect(String text) {
        Map<ScriptType, Integer> counts = new EnumMap<>(ScriptType.class);
        int letters = 0;
        int kana = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (!Character.isLetter(cp)) {
                continue;
            }
            letters++;
            ScriptType type = classify(Character.UnicodeScript.of(cp));
            if (type == ScriptType.JAPANESE) {
                kana++;
            }
            counts.merge(type, 1, Integer::sum);
        }
        if (letters == 0) {
            return ScriptType.UNKNOWN;
        }
        // Kana is only used by Japanese, so any kana makes Han characters Japanese too.
        if (kana > 0 && counts.containsKey(ScriptType.HAN)) {
            counts.merge(ScriptType.JAPANESE, counts.remove(ScriptType.HAN), Integer::sum);
        }
        ScriptType best = null;
        int bestCount = 0;
        for (Map.Entry<ScriptType, Integer> e : counts.entrySet()) {
            if (e.getKey() != ScriptType.LATIN && e.getValue() > bestCount) {
                best = e.getKey();
                bestCount = e.getValue();
            }
        }
        int latin = counts.getOrDefault(ScriptType.LATIN, 0);
        if (best == null) {
            return ScriptType.LATIN;
        }
        return (double) bestCount / letters >= NON_LATIN_SHARE || latin == 0 ? best : ScriptType.LATIN;
    }

    /** The language a script identifies by itself, or null when several languages share it (Devanagari, Arabic, Latin...). */
    public String languageFor(ScriptType script) {
        return switch (script) {
            case TAMIL -> "ta";
            case TELUGU -> "te";
            case MALAYALAM -> "ml";
            case KANNADA -> "kn";
            case BENGALI -> "bn";
            case HANGUL -> "ko";
            case JAPANESE -> "ja";
            case HAN -> "zh";
            default -> null;
        };
    }

    private static ScriptType classify(Character.UnicodeScript s) {
        return switch (s) {
            case LATIN -> ScriptType.LATIN;
            case TAMIL -> ScriptType.TAMIL;
            case DEVANAGARI -> ScriptType.DEVANAGARI;
            case TELUGU -> ScriptType.TELUGU;
            case MALAYALAM -> ScriptType.MALAYALAM;
            case KANNADA -> ScriptType.KANNADA;
            case BENGALI -> ScriptType.BENGALI;
            case ARABIC -> ScriptType.ARABIC;
            case HIRAGANA, KATAKANA -> ScriptType.JAPANESE;
            case HANGUL -> ScriptType.HANGUL;
            case HAN -> ScriptType.HAN;
            case CYRILLIC -> ScriptType.CYRILLIC;
            default -> ScriptType.OTHER;
        };
    }
}
