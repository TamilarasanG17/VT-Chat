package com.vtchat.service;

import com.vtchat.config.AppProperties;
import com.vtchat.exception.ApiException;
import jakarta.annotation.PostConstruct;
import com.vtchat.storage.CloudinaryRef;
import com.vtchat.storage.LogSanitizer;
import com.vtchat.storage.StorageException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class AvatarStorageService {

    private static final Pattern STORED_NAME =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.jpg$");
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("image/jpeg", "image/png");
    private static final Set<String> ALLOWED_FORMATS = Set.of("jpeg", "png");

    private static final int OUTPUT_SIZE = 512;
    private static final int MIN_SIDE = 32;
    private static final int MAX_SIDE = 8000;
    private static final long MAX_PIXELS = 25_000_000L;
    private static final int MAX_STORED_BYTES = 5 * 1024 * 1024;

    private final AppProperties props;
    private final MediaStorageService storage;
    private Path avatarDir;

    @PostConstruct
    void init() {
        try {
            avatarDir = Paths.get(props.getStorage().getUploadDir()).toAbsolutePath().normalize().resolve("avatars");
            Files.createDirectories(avatarDir);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create upload directory " + props.getStorage().getUploadDir(), e);
        }
    }

    public String store(MultipartFile file, Long userId) {
        byte[] jpeg = validateAndEncode(file);
        try {
            return storage.store(MediaStorageService.StoreRequest.avatar(userId, "jpg", "image/jpeg", jpeg.length), new ByteArrayInputStream(jpeg));
        } catch (StorageException e) {
            throw e;
        } catch (IOException e) {
            log.error("Could not write avatar operation=avatar-store error={}", LogSanitizer.describe(e));
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "STORAGE_ERROR",
                    "Could not save the image. Please try again.");
        }
    }

    public byte[] validateAndEncode(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("EMPTY_FILE", "Please choose an image to upload");
        }
        long maxMb = props.getStorage().getMaxAvatarMb();
        if (file.getSize() > maxMb * 1024 * 1024) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "FILE_TOO_LARGE",
                    "Image must be " + maxMb + " MB or smaller");
        }
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_CONTENT_TYPES.contains(contentType.toLowerCase(Locale.ROOT))) {
            throw ApiException.badRequest("UNSUPPORTED_FILE_TYPE", "Only JPEG and PNG images are allowed");
        }
        try {
            return toSquareJpeg(file.getBytes());
        } catch (ApiException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            log.debug("Rejected avatar upload error={}", LogSanitizer.describe(e));
            throw ApiException.badRequest("INVALID_IMAGE", "That file is not a valid image");
        }
    }

    public static boolean isLegacy(String ref) {
        return ref != null && STORED_NAME.matcher(ref).matches();
    }

    public Optional<byte[]> load(String ref) {
        try {
            if (isLegacy(ref)) {
                Path path = resolve(ref);
                return path != null && Files.isRegularFile(path) ? Optional.of(Files.readAllBytes(path)) : Optional.empty();
            }
            if (ref == null || !(ref.startsWith("avatars/") || CloudinaryRef.isRef(ref))) {
                return Optional.empty();
            }
            Optional<Resource> resource = storage.retrieve(ref);
            if (resource.isEmpty()) {
                return Optional.empty();
            }
            try (InputStream in = resource.get().getInputStream()) {
                return Optional.of(in.readNBytes(MAX_STORED_BYTES));
            }
        } catch (IOException e) {
            log.warn("Could not read avatar operation=avatar-read error={}", LogSanitizer.describe(e));
            return Optional.empty();
        }
    }

    public void delete(String ref) {
        if (ref == null) {
            return;
        }
        if (isLegacy(ref)) {
            Path path = resolve(ref);
            if (path != null) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    log.warn("Could not delete a legacy avatar file operation=avatar-delete error={}", LogSanitizer.describe(e));
                }
            }
        } else if (ref.startsWith("avatars/") || CloudinaryRef.isRef(ref)) {
            storage.delete(ref);
        }
    }

    public Optional<String> publicUrl(String ref) {
        return CloudinaryRef.isRef(ref) ? storage.publicUrl(ref) : Optional.empty();
    }

    public Optional<byte[]> readLocal(String ref) {
        if (isLegacy(ref)) {
            return readLegacy(ref);
        }
        if (ref == null || !ref.startsWith("avatars/")) {
            return Optional.empty();
        }
        try {
            Optional<Resource> resource = storage.retrieve(ref);
            if (resource.isEmpty()) {
                return Optional.empty();
            }
            try (InputStream in = resource.get().getInputStream()) {
                return Optional.of(in.readNBytes(MAX_STORED_BYTES));
            }
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    public Optional<byte[]> readLegacy(String name) {
        Path path = resolve(name);
        try {
            return path != null && Files.isRegularFile(path) ? Optional.of(Files.readAllBytes(path)) : Optional.empty();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private Path resolve(String filename) {
        if (filename == null || !STORED_NAME.matcher(filename).matches()) {
            return null;
        }
        Path path = avatarDir.resolve(filename).normalize();
        return path.startsWith(avatarDir) ? path : null;
    }

    private byte[] toSquareJpeg(byte[] data) throws IOException {
        BufferedImage source;
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(data))) {
            Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
            if (in == null || !readers.hasNext()) {
                throw ApiException.badRequest("INVALID_IMAGE", "That file is not a valid image");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(in, true, true);
                if (!ALLOWED_FORMATS.contains(reader.getFormatName().toLowerCase(Locale.ROOT))) {
                    throw ApiException.badRequest("UNSUPPORTED_FILE_TYPE", "Only JPEG and PNG images are allowed");
                }
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < MIN_SIDE || height < MIN_SIDE) {
                    throw ApiException.badRequest("INVALID_IMAGE", "Image is too small (minimum " + MIN_SIDE + "x" + MIN_SIDE + " pixels)");
                }
                if (width > MAX_SIDE || height > MAX_SIDE || (long) width * height > MAX_PIXELS) {
                    throw ApiException.badRequest("INVALID_IMAGE", "Image dimensions are too large");
                }
                source = reader.read(0);
            } finally {
                reader.dispose();
            }
        }
        if (source == null) {
            throw ApiException.badRequest("INVALID_IMAGE", "That file is not a valid image");
        }

        int side = Math.min(source.getWidth(), source.getHeight());
        BufferedImage square = source.getSubimage((source.getWidth() - side) / 2, (source.getHeight() - side) / 2, side, side);
        int target = Math.min(side, OUTPUT_SIZE);

        BufferedImage output = new BufferedImage(target, target, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = output.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, target, target);
            Image scaled = square.getScaledInstance(target, target, Image.SCALE_SMOOTH);
            g.drawImage(scaled, 0, 0, null);
        } finally {
            g.dispose();
        }

        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ByteArrayOutputStream bytes = new ByteArrayOutputStream();
             ImageOutputStream out = ImageIO.createImageOutputStream(bytes)) {
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.88f);
            writer.setOutput(out);
            writer.write(null, new IIOImage(output, null, null), param);
            out.flush();
            return bytes.toByteArray();
        } finally {
            writer.dispose();
        }
    }
}
