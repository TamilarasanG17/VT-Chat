package com.vtchat.service;

import com.vtchat.config.AppProperties;
import com.vtchat.entity.MessageType;
import com.vtchat.exception.ApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MediaFileInspector {

    public record Detected(MessageType type, String extension, String mimeType) {
    }

    private enum Signature { JPEG, PNG, GIF, WEBP, ISO_BMFF, EBML, PDF, OLE2, ZIP, TEXT }

    private record Rule(MessageType type, String mime, Signature signature) {
    }

    private static final String DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private static final Map<String, Rule> RULES = Map.ofEntries(
            Map.entry("jpg", new Rule(MessageType.IMAGE, "image/jpeg", Signature.JPEG)),
            Map.entry("jpeg", new Rule(MessageType.IMAGE, "image/jpeg", Signature.JPEG)),
            Map.entry("png", new Rule(MessageType.IMAGE, "image/png", Signature.PNG)),
            Map.entry("gif", new Rule(MessageType.IMAGE, "image/gif", Signature.GIF)),
            Map.entry("webp", new Rule(MessageType.IMAGE, "image/webp", Signature.WEBP)),
            Map.entry("mp4", new Rule(MessageType.VIDEO, "video/mp4", Signature.ISO_BMFF)),
            Map.entry("mov", new Rule(MessageType.VIDEO, "video/quicktime", Signature.ISO_BMFF)),
            Map.entry("webm", new Rule(MessageType.VIDEO, "video/webm", Signature.EBML)),
            Map.entry("pdf", new Rule(MessageType.FILE, "application/pdf", Signature.PDF)),
            Map.entry("doc", new Rule(MessageType.FILE, "application/msword", Signature.OLE2)),
            Map.entry("docx", new Rule(MessageType.FILE, DOCX, Signature.ZIP)),
            Map.entry("txt", new Rule(MessageType.FILE, "text/plain", Signature.TEXT)),
            Map.entry("xls", new Rule(MessageType.FILE, "application/vnd.ms-excel", Signature.OLE2)),
            Map.entry("xlsx", new Rule(MessageType.FILE, XLSX, Signature.ZIP)),
            Map.entry("zip", new Rule(MessageType.FILE, "application/zip", Signature.ZIP)));

    private static final int HEAD = 16;
    private static final int TEXT_HEAD = 8192;

    private final AppProperties props;

    public Detected inspect(MultipartFile file) {
        requireFile(file);
        Rule rule = RULES.get(extensionOf(file.getOriginalFilename()));
        if (rule == null) {
            throw unsupported("This file type is not supported");
        }
        requireSize(file, rule.type());
        byte[] head = readHead(file, rule.signature() == Signature.TEXT ? TEXT_HEAD : HEAD);
        if (!matches(rule.signature(), head)) {
            throw unsupported("The file content does not match its type");
        }
        return new Detected(rule.type(), extensionOf(file.getOriginalFilename()).replace("jpeg", "jpg"), rule.mime());
    }

    public Detected inspectVoice(MultipartFile file) {
        requireFile(file);
        requireSize(file, MessageType.VOICE);
        byte[] h = readHead(file, HEAD);
        if (matches(Signature.EBML, h)) return new Detected(MessageType.VOICE, "webm", "audio/webm");
        if (startsWith(h, 0, "OggS")) return new Detected(MessageType.VOICE, "ogg", "audio/ogg");
        if (matches(Signature.ISO_BMFF, h)) return new Detected(MessageType.VOICE, "m4a", "audio/mp4");
        if (startsWith(h, 0, "RIFF") && startsWith(h, 8, "WAVE")) return new Detected(MessageType.VOICE, "wav", "audio/wav");
        if (startsWith(h, 0, "ID3") || (h.length > 1 && (h[0] & 0xFF) == 0xFF && (h[1] & 0xE0) == 0xE0)) {
            return new Detected(MessageType.VOICE, "mp3", "audio/mpeg");
        }
        throw unsupported("This audio format is not supported");
    }

    private void requireFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EMPTY_FILE", "Please choose a file to send");
        }
    }

    private void requireSize(MultipartFile file, MessageType type) {
        long max = props.getMedia().maxBytes(type);
        if (file.getSize() > max) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                    "That file is too large (maximum " + (max / (1024 * 1024)) + " MB)");
        }
    }

    private static ApiException unsupported(String message) {
        return new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_FILE_TYPE", message);
    }

    static String extensionOf(String filename) {
        if (filename == null) return "";
        String name = filename.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) return "";
        String ext = name.substring(dot + 1).toLowerCase(Locale.ROOT);
        return ext.matches("[a-z0-9]{1,5}") ? ext : "";
    }

    private static byte[] readHead(MultipartFile file, int length) {
        try (InputStream in = file.getInputStream()) {
            return in.readNBytes(length);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "UPLOAD_FAILED", "Unable to read the uploaded file");
        }
    }

    private static boolean startsWith(byte[] b, int offset, String ascii) {
        if (b.length < offset + ascii.length()) return false;
        for (int i = 0; i < ascii.length(); i++) {
            if (b[offset + i] != (byte) ascii.charAt(i)) return false;
        }
        return true;
    }

    private static boolean bytesAre(byte[] b, int... expected) {
        if (b.length < expected.length) return false;
        for (int i = 0; i < expected.length; i++) {
            if ((b[i] & 0xFF) != expected[i]) return false;
        }
        return true;
    }

    private static boolean matches(Signature signature, byte[] b) {
        return switch (signature) {
            case JPEG -> bytesAre(b, 0xFF, 0xD8, 0xFF);
            case PNG -> bytesAre(b, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case GIF -> startsWith(b, 0, "GIF87a") || startsWith(b, 0, "GIF89a");
            case WEBP -> startsWith(b, 0, "RIFF") && startsWith(b, 8, "WEBP");
            case ISO_BMFF -> startsWith(b, 4, "ftyp");
            case EBML -> bytesAre(b, 0x1A, 0x45, 0xDF, 0xA3);
            case PDF -> startsWith(b, 0, "%PDF-");
            case OLE2 -> bytesAre(b, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
            case ZIP -> bytesAre(b, 'P', 'K', 3, 4) || bytesAre(b, 'P', 'K', 5, 6) || bytesAre(b, 'P', 'K', 7, 8);
            case TEXT -> isPlainText(b);
        };
    }

    private static boolean isPlainText(byte[] b) {
        for (byte value : b) {
            int c = value & 0xFF;
            if (c == 0 || (c < 0x20 && c != '\t' && c != '\n' && c != '\r' && c != '\f')) {
                return false;
            }
        }
        return true;
    }
}
