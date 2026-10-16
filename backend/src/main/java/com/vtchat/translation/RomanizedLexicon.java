package com.vtchat.translation;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Word lists used ONLY as detection / normalization signals for Romanized text. Nothing here translates a message:
 * a word on a list adds evidence that the text is Romanized Tamil (or Hindi), and for Tamil a spelling helper turns
 * very common Thanglish spellings into Tamil script before the real translation provider is called.
 * Lower-case Latin only. Easy to extend: add words, or add a language to {@link RomanizedLanguageDetector}.
 */
final class RomanizedLexicon {

    private RomanizedLexicon() {
    }

    /** Immutable set that quietly ignores repeated entries (Set.of would throw on a duplicate). */
    private static Set<String> words(String... values) {
        return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
    }

    /** Words that are practically never English: strong evidence of Romanized Tamil. */
    static final Set<String> TAMIL_STRONG = words(
            "epdi", "eppadi", "eppdi", "ebdi", "iruka", "irukka", "iruku", "irukku", "irukinga", "irukeenga", "irukken", "iruken",
            "irukeen", "irukiya", "irukkiya", "irukanga", "irundhen", "irunden", "irundhuchu", "irundha", "iruntha",
            "saptiya", "saapteengala", "saptingala", "saptacha", "saptu", "saaptu", "saptutu", "sapadu", "saapadu", "saptiya",
            "enna", "ennaku", "enaku", "enakku", "unaku", "unakku", "ungaluku", "ungalukku", "ungaloda", "unga", "ennada", "ennadi",
            "naan", "nee", "neenga", "neengal", "avan", "aval", "avanga", "ivan", "ival",
            "panra", "pannra", "panren", "pannren", "pannunga", "pannu", "pannuven", "pannitu", "pannitten", "panniten", "pannala", "pannalam", "panlam",
            "poriya", "porenga", "poren", "poran", "pora", "pogalam", "ponga", "poitu", "poitten", "poyiten", "pochu",
            "vaanga", "vaa", "vara", "varen", "varuven", "varala", "vandhutten", "vandhuten", "vandhen", "vanthen",
            "mudinjuchu", "mudinjucha", "mudinjacha", "mudinjathu", "mudinjidum", "mudiyala", "mudiyathu", "mudiyum",
            "enga", "engaye", "engayo", "ange", "inge", "anga", "inga",
            "romba", "nalla", "nallaa", "nalladhu", "venum", "venam", "vendam", "illai", "illama", "illaye",
            "aama", "aamam", "seri", "theriyum", "theriyala", "theriyadhu", "therila", "puriyala", "puriyuthu",
            "paaru", "paaren", "paathen", "paakalam", "sollu", "sollunga", "solren", "sonnen", "sollitten", "solla", "sollala",
            "kelu", "kelunga", "ketten", "kudu", "kudunga", "kodu", "thanga", "vechiruken", "vachiruken",
            "ippo", "ipo", "ippodhu", "appo", "appuram", "apram", "aprom", "eppo", "eppodhu", "epo", "yaar", "yaaru", "yen",
            "dhan", "thaan", "dhaan", "machan", "machaan", "macha", "mapla", "kanna", "chellam", "thambi", "thangachi",
            "konjam", "oru", "ellam", "ellarum", "kovam", "sirika", "aachu", "aayiduchu", "ayiduchu", "aagum", "ennaachu", "ennaachu",
            "seekiram", "seekirama", "sikkiram", "kudikka", "thoongu", "thoonga", "thoongitta",
            "nalaiku", "naalaiku", "naalaikku", "nethu", "netru", "inniku", "innaiku", "innikku",
            "sema", "thalaiva", "paravala", "kashtam", "kastam", "santhosham", "sandhosham", "nandri", "vanakkam", "vanakam",
            "veetla", "veettula", "vittula", "veedu", "oda", "kooda", "udan");

    /** Short or ambiguous words (also real English / names): weak evidence only. */
    static final Set<String> TAMIL_WEAK = words("ku", "la", "da", "di", "pa", "ma", "na", "ya", "nu", "illa", "sari", "vera", "anna", "akka", "amma", "appa", "naa");

    static final Set<String> HINDI_STRONG = words(
            "kya", "kaise", "kaisa", "kaisi", "hai", "hain", "nahi", "nahin", "haan", "accha", "acha", "theek", "thik", "bhai", "yaar",
            "mujhe", "tumhe", "tum", "aap", "mera", "meri", "tera", "teri", "kahan", "kab", "kyun", "kyu", "karo", "raha", "rahi", "rahe",
            "chalo", "bhejo", "batao", "bata", "dekho", "samajh", "pata", "bahut", "bohot", "bohut", "kuch", "kaun", "ghar", "paisa",
            "khana", "khaya", "pani", "thoda", "zyada", "jaldi", "sunao", "suno", "bolo", "kal", "aaj", "abhi", "mat", "bilkul", "dost");

    static final Set<String> HINDI_WEAK = words("ka", "ki", "ke", "ko", "se", "bol", "chal", "kar");

    /** Common English words (and English loanwords used inside Thanglish). They are never counted as Romanized evidence and are left untouched by transliteration. */
    static final Set<String> ENGLISH = words(
            "a", "an", "the", "i", "you", "he", "she", "it", "we", "they", "me", "my", "your", "his", "her", "our", "their",
            "is", "am", "are", "was", "were", "be", "been", "do", "does", "did", "have", "has", "had", "will", "would", "can", "could", "should",
            "to", "of", "in", "on", "at", "for", "with", "from", "by", "about", "and", "or", "but", "if", "so", "not", "no", "yes",
            "this", "that", "these", "those", "what", "where", "when", "why", "how", "who", "which", "there", "here", "then", "than", "now",
            "very", "much", "more", "most", "some", "any", "all", "just", "also", "too", "again", "please", "thanks", "thank", "sorry", "ok", "okay", "hi", "hello", "bye",
            "good", "bad", "great", "nice", "fine", "super", "cool", "busy", "free", "late", "early", "ready", "done", "sure", "right", "wrong",
            "today", "tomorrow", "yesterday", "tonight", "morning", "night", "evening", "afternoon", "time", "day", "week", "month", "year",
            "office", "meeting", "lunch", "dinner", "breakfast", "coffee", "tea", "call", "phone", "mobile", "message", "msg", "text", "mail", "email", "whatsapp",
            "bus", "train", "bike", "car", "auto", "flight", "ticket", "college", "school", "class", "exam", "test", "work", "job", "project", "report",
            "home", "house", "room", "shop", "market", "movie", "film", "song", "game", "match", "party", "birthday", "wedding", "money", "bill", "pay",
            "come", "go", "going", "come", "see", "look", "tell", "say", "ask", "send", "give", "take", "get", "got", "need", "want", "like", "love", "know",
            "wait", "stop", "start", "finish", "finished", "reach", "reached", "reaching", "leave", "left", "back", "out", "up", "down", "over", "off",
            "bro", "man", "guys", "friend", "sir", "madam", "problem", "issue", "update", "status", "plan", "idea", "point", "number", "address", "location");

    /**
     * Spelling help for very common Thanglish words: Latin spelling -> the usual Tamil-script spelling. Used for
     * normalization before translation. Words not listed are transliterated by rules in {@link TamilTransliterator}.
     */
    static final Map<String, String> TAMIL_SPELLING = Map.ofEntries(
            Map.entry("epdi", "எப்படி"), Map.entry("eppadi", "எப்படி"), Map.entry("eppdi", "எப்படி"), Map.entry("ebdi", "எப்படி"),
            Map.entry("iruka", "இருக்கா"), Map.entry("irukka", "இருக்கா"), Map.entry("iruku", "இருக்கு"), Map.entry("irukku", "இருக்கு"),
            Map.entry("irukinga", "இருக்கீங்க"), Map.entry("irukeenga", "இருக்கீங்க"), Map.entry("iruken", "இருக்கேன்"), Map.entry("irukken", "இருக்கேன்"),
            Map.entry("saptiya", "சாப்டியா"), Map.entry("saapteengala", "சாப்பிட்டீங்களா"), Map.entry("saptingala", "சாப்பிட்டீங்களா"),
            Map.entry("saptacha", "சாப்டாச்சா"), Map.entry("sapadu", "சாப்பாடு"), Map.entry("saapadu", "சாப்பாடு"),
            Map.entry("enna", "என்ன"), Map.entry("ennaku", "எனக்கு"), Map.entry("enaku", "எனக்கு"), Map.entry("enakku", "எனக்கு"),
            Map.entry("unaku", "உனக்கு"), Map.entry("unakku", "உனக்கு"), Map.entry("ungaluku", "உங்களுக்கு"), Map.entry("ungalukku", "உங்களுக்கு"),
            Map.entry("unga", "உங்க"), Map.entry("naan", "நான்"), Map.entry("naa", "நான்"), Map.entry("nee", "நீ"), Map.entry("neenga", "நீங்க"),
            Map.entry("avan", "அவன்"), Map.entry("aval", "அவள்"), Map.entry("avanga", "அவங்க"),
            Map.entry("panra", "பண்ற"), Map.entry("pannra", "பண்ற"), Map.entry("panren", "பண்றேன்"), Map.entry("pannren", "பண்றேன்"),
            Map.entry("pannunga", "பண்ணுங்க"), Map.entry("pannu", "பண்ணு"),
            Map.entry("poriya", "போறியா"), Map.entry("pora", "போற"), Map.entry("poren", "போறேன்"), Map.entry("poran", "போறான்"), Map.entry("porenga", "போறீங்க"),
            Map.entry("enga", "எங்க"), Map.entry("inga", "இங்க"), Map.entry("anga", "அங்க"),
            Map.entry("romba", "ரொம்ப"), Map.entry("nalla", "நல்ல"), Map.entry("nallaa", "நல்லா"),
            Map.entry("venum", "வேணும்"), Map.entry("venam", "வேணாம்"), Map.entry("vendam", "வேண்டாம்"),
            Map.entry("illa", "இல்ல"), Map.entry("illai", "இல்லை"), Map.entry("aama", "ஆமா"), Map.entry("aamam", "ஆமாம்"), Map.entry("seri", "சரி"),
            Map.entry("ippo", "இப்போ"), Map.entry("ipo", "இப்போ"), Map.entry("appuram", "அப்புறம்"), Map.entry("apram", "அப்புறம்"),
            Map.entry("yen", "ஏன்"), Map.entry("eppo", "எப்போ"), Map.entry("epo", "எப்போ"), Map.entry("yaar", "யார்"), Map.entry("yaaru", "யாரு"),
            Map.entry("mudinjuchu", "முடிஞ்சுச்சு"), Map.entry("mudinjucha", "முடிஞ்சுச்சா"), Map.entry("mudinjacha", "முடிஞ்சுச்சா"),
            Map.entry("mudinjathu", "முடிஞ்சது"), Map.entry("mudiyala", "முடியல"),
            Map.entry("theriyum", "தெரியும்"), Map.entry("theriyala", "தெரியல"), Map.entry("puriyala", "புரியல"),
            Map.entry("konjam", "கொஞ்சம்"), Map.entry("oru", "ஒரு"), Map.entry("ellam", "எல்லாம்"),
            Map.entry("inniku", "இன்னிக்கு"), Map.entry("naalaiku", "நாளைக்கு"), Map.entry("nethu", "நேத்து"),
            Map.entry("vaanga", "வாங்க"), Map.entry("vaa", "வா"), Map.entry("varen", "வரேன்"), Map.entry("vara", "வர"), Map.entry("varala", "வரல"),
            Map.entry("vandhen", "வந்தேன்"), Map.entry("vandhutten", "வந்துட்டேன்"), Map.entry("vandhuten", "வந்துட்டேன்"),
            Map.entry("paaru", "பாரு"), Map.entry("sollu", "சொல்லு"), Map.entry("sonnen", "சொன்னேன்"), Map.entry("kelu", "கேளு"), Map.entry("kudu", "குடு"),
            Map.entry("seekiram", "சீக்கிரம்"), Map.entry("sema", "செம"), Map.entry("machan", "மச்சான்"), Map.entry("macha", "மச்சா"),
            Map.entry("nandri", "நன்றி"), Map.entry("vanakkam", "வணக்கம்"), Map.entry("thaan", "தான்"), Map.entry("dhaan", "தான்"),
            Map.entry("ku", "க்கு"), Map.entry("la", "ல"), Map.entry("oda", "ஓட"), Map.entry("kooda", "கூட"), Map.entry("da", "டா"), Map.entry("di", "டி"),
            Map.entry("thambi", "தம்பி"), Map.entry("anna", "அண்ணா"), Map.entry("akka", "அக்கா"), Map.entry("amma", "அம்மா"), Map.entry("appa", "அப்பா"));
}
