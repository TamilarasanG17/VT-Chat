package com.vtchat.service;

import com.vtchat.config.RagProperties;
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
public class KnowledgeFileInspector {

    public record Detected(String extension, String mimeType, boolean viewableInBrowser) {
    }

    private enum Signature { PDF, OLE2, ZIP, TEXT }

    private record Rule(String mime, boolean viewable, Signature signature) {
    }

    private static final Map<String, Rule> RULES = Map.of(
            "pdf", new Rule("application/pdf", true, Signature.PDF),
            "txt", new Rule("text/plain; charset=UTF-8", true, Signature.TEXT),
            "md", new Rule("text/plain; charset=UTF-8", true, Signature.TEXT),
            "doc", new Rule("application/msword", false, Signature.OLE2),
            "docx", new Rule("application/vnd.openxmlformats-officedocument.wordprocessingml.document", false, Signature.ZIP));

    private static final int HEAD = 8192;

    private final RagProperties props;

    public Detected inspect(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EMPTY_FILE", "Please choose a file to upload");
        }
        String ext = extensionOf(file.getOriginalFilename());
        Rule rule = RULES.get(ext);
        if (rule == null) {
            throw unsupported("Supported documents: PDF, TXT, MD, DOC and DOCX");
        }
        if (file.getSize() > props.maxDocumentBytes()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                    "That file is too large (maximum " + props.getMaxDocumentSizeMb() + " MB)");
        }
        byte[] head;
        try (InputStream in = file.getInputStream()) {
            head = in.readNBytes(HEAD);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "UPLOAD_FAILED", "Unable to read the uploaded file");
        }
        if (!matches(rule.signature(), head)) {
            throw unsupported("The file content does not match its type");
        }
        return new Detected(ext, rule.mime(), rule.viewable());
    }

    public static String mimeFor(String extension) {
        Rule rule = RULES.get(extension == null ? "" : extension.toLowerCase(Locale.ROOT));
        return rule == null ? "application/octet-stream" : rule.mime();
    }

    public static boolean viewable(String extension) {
        Rule rule = RULES.get(extension == null ? "" : extension.toLowerCase(Locale.ROOT));
        return rule != null && rule.viewable();
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

    private static boolean matches(Signature signature, byte[] b) {
        return switch (signature) {
            case PDF -> startsWith(b, "%PDF-");
            case OLE2 -> bytesAre(b, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
            case ZIP -> bytesAre(b, 'P', 'K', 3, 4);
            case TEXT -> isText(b);
        };
    }

    private static boolean startsWith(byte[] b, String ascii) {
        if (b.length < ascii.length()) return false;
        for (int i = 0; i < ascii.length(); i++) {
            if (b[i] != (byte) ascii.charAt(i)) return false;
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

    private static boolean isText(byte[] b) {
        if (bytesAre(b, 0xFF, 0xFE) || bytesAre(b, 0xFE, 0xFF)) {
            return true;
        }
        for (byte value : b) {
            int c = value & 0xFF;
            if (c == 0 || (c < 0x20 && c != '\t' && c != '\n' && c != '\r' && c != '\f')) {
                return false;
            }
        }
        return true;
    }
}
