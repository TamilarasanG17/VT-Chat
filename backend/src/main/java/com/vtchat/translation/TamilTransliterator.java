package com.vtchat.translation;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Normalizes Romanized Tamil (Thanglish) into Tamil script so a normal translation provider can read it.
 * <ol>
 *   <li>English words (office, meeting, lunch...) are left exactly as written;</li>
 *   <li>very common Thanglish words use their usual Tamil spelling ({@link RomanizedLexicon#TAMIL_SPELLING});</li>
 *   <li>every other word is converted by phonetic rules (a simplified ITRANS-like scheme tuned for chat spelling).</li>
 * </ol>
 * Chat spelling is not standardized (vowel length and the several Tamil n/l/r/t letters are not written), so this is a
 * best effort that helps the provider; it is not a perfect transliteration. Punctuation, digits and spacing are kept.
 */
@Component
public class TamilTransliterator {

    private static final Pattern WORD = Pattern.compile("[A-Za-z]+(?:'[A-Za-z]+)?");
    private static final char PULLI = '்';

    public String transliterate(String text) {
        Matcher m = WORD.matcher(text);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            m.appendReplacement(out, Matcher.quoteReplacement(convertWord(m.group())));
        }
        m.appendTail(out);
        return out.toString();
    }

    private String convertWord(String original) {
        String w = original.toLowerCase(Locale.ROOT).replace("'", "");
        String spelled = RomanizedLexicon.TAMIL_SPELLING.get(w);
        if (spelled != null) {
            return spelled;
        }
        if (w.length() <= 1 || RomanizedLexicon.ENGLISH.contains(w)) {
            return original;
        }
        return rules(w);
    }

    // ------------------------------------------------------------------ phonetic rules

    private record Vowel(String letters, String independent, String sign) {
    }

    // Longest spellings first.
    private static final Vowel[] VOWELS = {
            new Vowel("aa", "ஆ", "ா"), new Vowel("ai", "ஐ", "ை"), new Vowel("au", "ஔ", "ௌ"),
            new Vowel("ee", "ஈ", "ீ"), new Vowel("ii", "ஈ", "ீ"), new Vowel("oo", "ஊ", "ூ"), new Vowel("uu", "ஊ", "ூ"),
            new Vowel("a", "அ", ""), new Vowel("i", "இ", "ி"), new Vowel("u", "உ", "ு"),
            new Vowel("e", "எ", "ெ"), new Vowel("o", "ஒ", "ொ"),
    };

    private record Consonant(String letters, String tamil) {
    }

    private static final Consonant[] CONSONANTS = {
            new Consonant("nj", "ஞ"), new Consonant("ng", "ங"), new Consonant("zh", "ழ"), new Consonant("ch", "ச"),
            new Consonant("sh", "ஷ"), new Consonant("th", "த"), new Consonant("dh", "த"), new Consonant("kh", "க"),
            new Consonant("gh", "க"), new Consonant("bh", "ப"), new Consonant("ph", "ப"),
            new Consonant("k", "க"), new Consonant("g", "க"), new Consonant("s", "ச"), new Consonant("j", "ஜ"),
            new Consonant("t", "ட"), new Consonant("d", "ட"), new Consonant("p", "ப"), new Consonant("b", "ப"),
            new Consonant("m", "ம"), new Consonant("y", "ய"), new Consonant("r", "ர"), new Consonant("l", "ல"),
            new Consonant("v", "வ"), new Consonant("w", "வ"), new Consonant("h", "ஹ"), new Consonant("z", "ழ"),
            new Consonant("q", "க"), new Consonant("c", "க"), new Consonant("f", "ஃப"), new Consonant("x", "க்ஸ"),
            new Consonant("n", "ன"),
    };

    private String rules(String w) {
        StringBuilder out = new StringBuilder();
        boolean pending = false;   // the last letter written is a consonant that has no vowel sign yet
        String previousConsonant = "";
        int i = 0;
        while (i < w.length()) {
            Vowel v = vowelAt(w, i);
            if (v != null) {
                if (pending) {
                    out.append(v.sign());
                } else {
                    out.append(v.independent());
                }
                pending = false;
                previousConsonant = "";
                i += v.letters().length();
                continue;
            }
            Consonant c = consonantAt(w, i);
            if (c == null) {
                i++;   // anything unexpected is skipped
                continue;
            }
            String tamil = c.tamil();
            int next = i + c.letters().length();
            if (c.letters().equals("n")) {
                tamil = nLetter(w, i, next);
            } else if (c.letters().equals("r") && (previousConsonant.equals("ன") || previousConsonant.equals("ர") || previousConsonant.equals("ற"))) {
                tamil = "ற";   // "nr" and "rr" are the strong r
            } else if (c.letters().equals("c")) {
                tamil = next < w.length() && "eiy".indexOf(w.charAt(next)) >= 0 ? "ச" : "க";
            }
            if (pending) {
                out.append(PULLI);
            }
            out.append(tamil);
            pending = true;
            previousConsonant = tamil;
            i = next;
        }
        if (pending) {
            out.append(PULLI);
        }
        return out.toString();
    }

    /** Tamil has three n letters; chat spelling does not say which, so use the common pattern. */
    private static String nLetter(String w, int start, int next) {
        if (start == 0) {
            return "ந";
        }
        if (w.startsWith("th", next) || w.startsWith("dh", next)) {
            return "ந";                       // vandhen -> வந்தேன்
        }
        if (next < w.length() && (w.charAt(next) == 't' || w.charAt(next) == 'd')) {
            return "ண";                       // kandu -> கண்டு
        }
        return "ன";
    }

    private static Vowel vowelAt(String w, int i) {
        for (Vowel v : VOWELS) {
            if (w.startsWith(v.letters(), i)) {
                return v;
            }
        }
        return null;
    }

    private static Consonant consonantAt(String w, int i) {
        for (Consonant c : CONSONANTS) {
            if (w.startsWith(c.letters(), i)) {
                return c;
            }
        }
        return null;
    }
}
