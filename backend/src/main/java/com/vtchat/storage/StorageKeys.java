package com.vtchat.storage;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StorageKeys {

    public static final String CHAT_MEDIA = "chat-media";
    public static final String KNOWLEDGE = "knowledge";
    public static final String AVATARS = "avatars";

    private static final String UUID_RE = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final Pattern LEGACY = Pattern.compile("^(images|videos|files|voice)/(" + UUID_RE + ")\\.([a-z0-9]{2,5})$");
    private static final Pattern CURRENT = Pattern.compile("^(chat-media|knowledge|avatars)/([0-9]{1,18})/(" + UUID_RE + ")\\.([a-z0-9]{2,5})$");
    private static final Pattern LEGACY_THUMB = Pattern.compile("^images/thumbs/" + UUID_RE + "\\.jpg$");
    private static final Pattern CURRENT_THUMB = Pattern.compile("^chat-media/[0-9]{1,18}/thumbs/" + UUID_RE + "\\.jpg$");

    private StorageKeys() {
    }

    public static String newKey(String area, long scope, String extension) {
        if (!CHAT_MEDIA.equals(area) && !KNOWLEDGE.equals(area) && !AVATARS.equals(area)) {
            throw new IllegalArgumentException("Unknown storage area");
        }
        String ext = extension == null ? "" : extension.toLowerCase();
        if (!ext.matches("[a-z0-9]{2,5}") || scope < 0) {
            throw new IllegalArgumentException("Invalid extension or scope");
        }
        return area + "/" + scope + "/" + UUID.randomUUID() + "." + ext;
    }

    public static boolean isLegacy(String key) {
        return key != null && (LEGACY.matcher(key).matches() || LEGACY_THUMB.matcher(key).matches());
    }

    public static boolean isValid(String key) {
        return key != null && (LEGACY.matcher(key).matches() || CURRENT.matcher(key).matches()
                || LEGACY_THUMB.matcher(key).matches() || CURRENT_THUMB.matcher(key).matches());
    }

    public static Optional<String> thumbnailKey(String key) {
        if (key == null) {
            return Optional.empty();
        }
        Matcher legacy = LEGACY.matcher(key);
        if (legacy.matches()) {
            return "images".equals(legacy.group(1)) ? Optional.of("images/thumbs/" + legacy.group(2) + ".jpg") : Optional.empty();
        }
        Matcher current = CURRENT.matcher(key);
        if (current.matches() && CHAT_MEDIA.equals(current.group(1))) {
            return Optional.of(CHAT_MEDIA + "/" + current.group(2) + "/thumbs/" + current.group(3) + ".jpg");
        }
        return Optional.empty();
    }

    public static Optional<String> extension(String key) {
        if (key == null) {
            return Optional.empty();
        }
        Matcher legacy = LEGACY.matcher(key);
        if (legacy.matches()) {
            return Optional.of(legacy.group(3));
        }
        Matcher current = CURRENT.matcher(key);
        return current.matches() ? Optional.of(current.group(4)) : Optional.empty();
    }

    public static Optional<String> uuid(String key) {
        if (key == null) {
            return Optional.empty();
        }
        Matcher legacy = LEGACY.matcher(key);
        if (legacy.matches()) {
            return Optional.of(legacy.group(2));
        }
        Matcher current = CURRENT.matcher(key);
        return current.matches() ? Optional.of(current.group(3)) : Optional.empty();
    }
}
