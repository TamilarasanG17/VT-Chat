package com.vtchat.storage;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class CloudinaryRef {

    public static final String PREFIX = "cld:";
    public static final String IMAGE = "image";
    public static final String VIDEO = "video";
    public static final String RAW = "raw";
    public static final String AUTHENTICATED = "authenticated";
    public static final String UPLOAD = "upload";

    private static final String SEGMENT = "[A-Za-z0-9][A-Za-z0-9_-]{0,63}";
    private static final String UUID_RE = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final Pattern REF = Pattern.compile(
            "^cld:([ivr])([au]):((?:" + SEGMENT + "/){1,4}(?:chat-media|knowledge|avatars)/[0-9]{1,18}/" + UUID_RE + ")(?:\\.([a-z0-9]{2,5}))?$");
    private static final Pattern FOLDER = Pattern.compile("^" + SEGMENT + "(/" + SEGMENT + "){0,3}$");

    private final String resourceType;
    private final String deliveryType;
    private final String publicId;
    private final String format;
    private final String area;

    private CloudinaryRef(String resourceType, String deliveryType, String publicId, String format, String area) {
        this.resourceType = resourceType;
        this.deliveryType = deliveryType;
        this.publicId = publicId;
        this.format = format;
        this.area = area;
    }

    public static boolean isRef(String key) {
        return key != null && key.startsWith(PREFIX);
    }

    public static Optional<CloudinaryRef> parse(String key) {
        if (key == null || key.length() > 255) {
            return Optional.empty();
        }
        Matcher m = REF.matcher(key);
        if (!m.matches()) {
            return Optional.empty();
        }
        String resource = switch (m.group(1)) {
            case "i" -> IMAGE;
            case "v" -> VIDEO;
            default -> RAW;
        };
        String delivery = "a".equals(m.group(2)) ? AUTHENTICATED : UPLOAD;
        String path = m.group(3);
        String ext = m.group(4);
        if (RAW.equals(resource)) {
            if (ext == null) {
                return Optional.empty();
            }
            return Optional.of(new CloudinaryRef(resource, delivery, path + "." + ext, null, areaOf(path)));
        }
        if (ext == null) {
            return Optional.empty();
        }
        return Optional.of(new CloudinaryRef(resource, delivery, path, ext, areaOf(path)));
    }

    public static Optional<CloudinaryRef> of(String resourceType, String deliveryType, String publicId, String format) {
        String r = IMAGE.equals(resourceType) ? "i" : VIDEO.equals(resourceType) ? "v" : RAW.equals(resourceType) ? "r" : null;
        String t = AUTHENTICATED.equals(deliveryType) ? "a" : UPLOAD.equals(deliveryType) ? "u" : null;
        if (r == null || t == null || publicId == null) {
            return Optional.empty();
        }
        String text = PREFIX + r + t + ":" + publicId + (RAW.equals(resourceType) || format == null ? "" : "." + format);
        return parse(text);
    }

    public static boolean validFolder(String folder) {
        return folder != null && FOLDER.matcher(folder).matches();
    }

    public static String newPublicId(String folder, String area, long scope, String uuid, String extension, boolean raw) {
        if (!validFolder(folder) || scope < 0 || !uuid.matches(UUID_RE)
                || !(StorageKeys.CHAT_MEDIA.equals(area) || StorageKeys.KNOWLEDGE.equals(area) || StorageKeys.AVATARS.equals(area))) {
            throw new IllegalArgumentException("Invalid asset parameters");
        }
        return folder + "/" + area + "/" + scope + "/" + uuid + (raw ? "." + extension : "");
    }

    private static String areaOf(String path) {
        for (String a : new String[]{StorageKeys.CHAT_MEDIA, StorageKeys.KNOWLEDGE, StorageKeys.AVATARS}) {
            if (path.contains("/" + a + "/")) {
                return a;
            }
        }
        return "";
    }

    public String resourceType() {
        return resourceType;
    }

    public String deliveryType() {
        return deliveryType;
    }

    public String publicId() {
        return publicId;
    }

    public String format() {
        return format;
    }

    public String area() {
        return area;
    }

    public boolean isImage() {
        return IMAGE.equals(resourceType);
    }

    public boolean isRaw() {
        return RAW.equals(resourceType);
    }

    public boolean isPublic() {
        return UPLOAD.equals(deliveryType);
    }

    public String key() {
        String r = IMAGE.equals(resourceType) ? "i" : VIDEO.equals(resourceType) ? "v" : "r";
        String t = isPublic() ? "u" : "a";
        return PREFIX + r + t + ":" + publicId + (format == null ? "" : "." + format);
    }

    public String tail() {
        int i = publicId.indexOf("/" + area + "/");
        String rest = i < 0 ? publicId : publicId.substring(i + 1);
        return rest + (format == null ? "" : "." + format);
    }

    @Override
    public String toString() {
        return "CloudinaryRef{" + resourceType + "/" + deliveryType + "}";
    }
}
