package com.vtchat.storage;

import java.security.SecureRandom;
import java.util.regex.Pattern;

public final class LogSanitizer {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String UUID_RE = "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}";

    private static final Pattern[] PATTERNS = {
            Pattern.compile("(?i)\\b[a-z][a-z0-9+.-]*://[^\\s\"'<>]+"),
            Pattern.compile("(?i)[?&]?X-Amz-[A-Za-z-]+=[^&\\s\"']*"),
            Pattern.compile("(?i)[?&]mt=[^&\\s\"']+"),
            Pattern.compile("(?i)\\b(Bearer|Basic)\\s+[A-Za-z0-9._~+/=-]+"),
            Pattern.compile("\\beyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]*"),
            Pattern.compile("\\bcld:[ivr][au]:[^\\s\"'<>,;]+"),
            Pattern.compile("\\bs--[A-Za-z0-9_-]{8,}--"),
            Pattern.compile("\\b(chat-media|knowledge|avatars)/[0-9]{1,18}/(thumbs/)?" + UUID_RE + "\\.[A-Za-z0-9]{2,5}"),
            Pattern.compile("\\b(images|videos|files|voice)/(thumbs/)?" + UUID_RE + "\\.[A-Za-z0-9]{2,5}"),
            Pattern.compile("\\b" + UUID_RE + "\\.(jpg|jpeg|png)\\b"),
            Pattern.compile("\\b(AKIA|ASIA)[A-Z0-9]{12,}\\b"),
            Pattern.compile("\\b[0-9a-f]{32}\\b"),
            Pattern.compile("(?i)\\b(secret[-_ ]?(access[-_ ]?)?key|access[-_ ]?key([-_ ]?id)?|password|passwd|token|api[-_ ]?key|api[-_ ]?secret|authorization|signature|credential)\\s*[=:]\\s*[^\\s,;\"']+"),
    };

    private LogSanitizer() {
    }

    public static String redact(String text) {
        if (text == null) {
            return null;
        }
        String out = text;
        for (Pattern p : PATTERNS) {
            out = p.matcher(out).replaceAll("[redacted]");
        }
        return out;
    }

    public static String describe(Throwable t) {
        StringBuilder sb = new StringBuilder();
        int depth = 0;
        for (Throwable c = t; c != null && depth < 4; c = c.getCause() == c ? null : c.getCause(), depth++) {
            if (depth > 0) {
                sb.append("<-");
            }
            sb.append(c.getClass().getSimpleName());
        }
        return sb.length() == 0 ? "unknown" : sb.toString();
    }

    public static boolean isTimeout(Throwable t) {
        int depth = 0;
        for (Throwable c = t; c != null && depth < 8; c = c.getCause() == c ? null : c.getCause(), depth++) {
            if (c.getClass().getSimpleName().contains("Timeout")) {
                return true;
            }
        }
        return false;
    }

    public static String opId() {
        return String.format("%08x", RANDOM.nextInt());
    }
}
