package com.vtchat.translation;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extra evidence for text written in Latin letters whose language normal detectors miss ("epdi iruka" is Tamil,
 * but a general detector says English or Indonesian). It scores how many words are typical of a Romanized language
 * and how much of the message they cover, so mixed messages such as "naan office ku poren" still count.
 * <p>
 * It never translates anything. It only answers "is this probably Romanized Tamil / Hindi?". The word lists live in
 * {@link RomanizedLexicon}; adding another language means adding a {@link Profile} below.
 */
@Component
public class RomanizedLanguageDetector {

    private static final Pattern WORD = Pattern.compile("[a-z]+(?:'[a-z]+)?");

    /** Sub-word spellings that are rare in English but common in Romanized Indian languages. */
    private static final List<Pattern> TAMIL_PATTERNS = List.of(
            Pattern.compile("zh"), Pattern.compile("ndh"), Pattern.compile("ngal"), Pattern.compile("aanga"),
            Pattern.compile("eenga"), Pattern.compile("[ui]kku$"));

    private record Profile(String language, Set<String> strong, Set<String> weak, List<Pattern> patterns) {
    }

    private static final List<Profile> PROFILES = List.of(
            new Profile("ta", RomanizedLexicon.TAMIL_STRONG, RomanizedLexicon.TAMIL_WEAK, TAMIL_PATTERNS),
            new Profile("hi", RomanizedLexicon.HINDI_STRONG, RomanizedLexicon.HINDI_WEAK, List.of()));

    /** @param decisive true when the evidence is so clear that no provider detection is needed */
    public record Detection(String language, double score, double coverage, boolean decisive) {
    }

    /**
     * @param providerLanguage what the general detector said (may be null)
     * @param providerConfidence its confidence 0..1 (may be null)
     * @return the Romanized language, if the evidence is strong enough
     */
    public Optional<Detection> detect(String text, String providerLanguage, Double providerConfidence) {
        List<String> words = words(text);
        if (words.isEmpty()) {
            return Optional.empty();
        }
        Detection best = null;
        for (Profile profile : PROFILES) {
            double score = score(profile, words);
            double coverage = score / words.size();
            if (best == null || score > best.score()) {
                best = new Detection(profile.language(), score, coverage, false);
            }
        }
        if (best == null || best.score() < 1.0) {
            return Optional.empty();
        }

        // The general detector is trusted more when it is confident, so ask for more evidence in that case.
        boolean providerSaysOther = providerLanguage != null && !providerLanguage.equals(best.language());
        double confidence = providerConfidence == null ? 0.5 : providerConfidence;
        double minCoverage = 0.3;
        double minScore = 1.0;
        if (providerSaysOther && confidence >= 0.9 && words.size() >= 4) {
            minCoverage = 0.4;
            minScore = 2.0;
        } else if (providerSaysOther && !"en".equals(providerLanguage) && confidence >= 0.8) {
            minCoverage = 0.6;
            minScore = 2.0;
        }
        if (best.score() < minScore || best.coverage() < minCoverage) {
            return Optional.empty();
        }
        boolean decisive = best.score() >= 2.0 && best.coverage() >= 0.6;
        return Optional.of(new Detection(best.language(), best.score(), best.coverage(), decisive));
    }

    /** Lower-case Latin words of the text (digits, punctuation and emoji are ignored). */
    static List<String> words(String text) {
        Matcher m = WORD.matcher(text.toLowerCase(Locale.ROOT));
        List<String> out = new java.util.ArrayList<>();
        while (m.find()) {
            out.add(m.group());
        }
        return out;
    }

    private static double score(Profile profile, List<String> words) {
        double total = 0;
        for (String w : words) {
            if (profile.strong().contains(w)) {
                total += 1.0;
            } else if (profile.weak().contains(w)) {
                total += 0.3;
            } else if (w.length() >= 5 && !RomanizedLexicon.ENGLISH.contains(w)) {
                for (Pattern p : profile.patterns()) {
                    if (p.matcher(w).find()) {
                        total += 0.4;
                        break;
                    }
                }
            }
        }
        return total;
    }
}
