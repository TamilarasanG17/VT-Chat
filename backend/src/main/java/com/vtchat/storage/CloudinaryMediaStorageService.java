package com.vtchat.storage;

import com.cloudinary.Cloudinary;
import com.cloudinary.Transformation;
import com.cloudinary.utils.ObjectUtils;
import com.vtchat.config.MediaStorageProperties;
import com.vtchat.service.MediaStorageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;

public class CloudinaryMediaStorageService implements MediaStorageService {

    private static final Logger log = LoggerFactory.getLogger(CloudinaryMediaStorageService.class);
    private static final String PROVIDER = "cloudinary";
    private static final long LINK_TTL_SECONDS = 300;
    private static final int THUMB_SIZE = 480;
    private static final long UNKNOWN_LENGTH_CAP = 100L * 1024 * 1024;
    private static final Pattern SAFE_NAME_CHARS = Pattern.compile("[^A-Za-z0-9_-]+");

    private final Cloudinary cloudinary;
    private final MediaStorageProperties.Cloudinary cfg;
    private final OrphanSink orphans;
    private final HttpClient http;
    private final ExecutorService executor;
    private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();

    public CloudinaryMediaStorageService(MediaStorageProperties.Cloudinary cfg, OrphanSink orphans) {
        this(newClient(cfg), cfg, orphans);
    }

    CloudinaryMediaStorageService(Cloudinary cloudinary, MediaStorageProperties.Cloudinary cfg, OrphanSink orphans) {
        this.cloudinary = cloudinary;
        this.cfg = cfg;
        this.orphans = orphans == null ? OrphanSink.NONE : orphans;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(1000, cfg.getConnectTimeoutMs())))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
        this.executor = new ThreadPoolExecutor(2, 16, 60, TimeUnit.SECONDS, new SynchronousQueue<>(), r -> {
            Thread t = new Thread(r, "cloudinary-call");
            t.setDaemon(true);
            return t;
        });
    }

    private static Cloudinary newClient(MediaStorageProperties.Cloudinary cfg) {
        return new Cloudinary(ObjectUtils.asMap(
                "cloud_name", cfg.getCloudName().trim(),
                "api_key", cfg.getApiKey().trim(),
                "api_secret", cfg.getApiSecret().trim(),
                "secure", true));
    }

    public void verify() {
        try {
            call("ping", cfg.getApiTimeoutMs(), () -> cloudinary.api().ping(ObjectUtils.emptyMap()));
            log.info("Cloudinary reachable (cloud '{}', folder '{}')", cfg.getCloudName(), cfg.getFolder());
        } catch (StorageException e) {
            String hint = switch (e.kind()) {
                case ACCESS_DENIED -> "the cloud name, API key or API secret is wrong (or the key is disabled)";
                case TIMEOUT, UNAVAILABLE -> "Cloudinary could not be reached from this server";
                default -> "unexpected answer";
            };
            log.error("Cloudinary check failed category={}: {}. Uploads will fail until this is fixed.", e.kind(),
                    hint);
        }
    }

    public long requests(String operation) {
        LongAdder a = counters.get(operation);
        return a == null ? 0 : a.sum();
    }

    @Override
    public String store(StoreRequest request, InputStream content) throws IOException {
        return storeAs(request, UUID.randomUUID().toString(), content, false);
    }

    public String storeDeterministic(StoreRequest request, String uuid, InputStream content) throws IOException {
        return storeAs(request, uuid, content, true);
    }

    // private String storeAs(StoreRequest request, String uuid, InputStream content, boolean overwrite)
    //         throws IOException {
    //     String ext = request.extension() == null ? "" : request.extension().toLowerCase(Locale.ROOT);
    //     if (!ext.matches("[a-z0-9]{2,5}")) {
    //         throw new IOException("Invalid extension");
    //     }
    //     Kind kind = kindFor(request, ext);
    //     String publicId;
    //     try {
    //         publicId = CloudinaryRef.newPublicId(cfg.getFolder(), request.area(), request.scope(), uuid, ext,
    //                 kind.raw());
    //     } catch (IllegalArgumentException e) {
    //         throw new IOException("Invalid asset parameters");
    //     }
    //     Path temp = Files.createTempFile("vtchat-upload-", ".tmp");
    //     try {
    //         long bytes = Files.copy(content, temp, StandardCopyOption.REPLACE_EXISTING);
    //         if (request.contentLength() >= 0 && bytes != request.contentLength()) {
    //             throw new IOException("Upload was incomplete");
    //         }
    //         Map<String, Object> options = ObjectUtils.asMap(
    //                 "resource_type", kind.resourceType(),
    //                 "type", kind.deliveryType(),
    //                 "public_id", publicId,
    //                 "overwrite", overwrite,
    //                 "unique_filename", false,
    //                 "use_filename", false);
    //         Map<?, ?> result;
    //         try {
    //             result = call("upload", cfg.getUploadTimeoutMs(),
    //                     () -> cloudinary.uploader().upload(temp.toFile(), options));
    //         } catch (StorageException e) {
    //             if (e.kind() == StorageException.Kind.TIMEOUT || e.kind() == StorageException.Kind.UNAVAILABLE) {
    //                 cleanup(kind, publicId, ext);
    //             }
    //             throw e;
    //         }
    //         String returnedFormat = text(result.get("format"));
    //         Optional<CloudinaryRef> ref = CloudinaryRef.of(text(result.get("resource_type")), text(result.get("type")),
    //                 text(result.get("public_id")), kind.raw() ? null : (returnedFormat == null ? ext : returnedFormat));
    //         long reported = result.get("bytes") instanceof Number n ? n.longValue() : -1;
    //         if (ref.isEmpty() || !publicId.equals(ref.get().publicId()) || (reported >= 0 && reported != bytes)) {
    //             cleanup(kind, publicId, ext);
    //             throw new StorageException(StorageException.Kind.FAILED, "upload");
    //         }
    //         return ref.get().key();
    //     } finally {
    //         Files.deleteIfExists(temp);
    //     }
    // }

    
private String storeAs(
        StoreRequest request,
        String uuid,
        InputStream content,
        boolean overwrite
) throws IOException {

    String ext = request.extension() == null
            ? ""
            : request.extension().toLowerCase(Locale.ROOT);

    if (!ext.matches("[a-z0-9]{2,5}")) {
        throw new IOException("Invalid extension");
    }

    Kind kind = kindFor(request, ext);

    String publicId;
    try {
        publicId = CloudinaryRef.newPublicId(
                cfg.getFolder(),
                request.area(),
                request.scope(),
                uuid,
                ext,
                kind.raw()
        );
    } catch (IllegalArgumentException e) {
        throw new IOException("Invalid asset parameters", e);
    }

    // Preserve the original extension for Cloudinary's upload handling.
    Path temp = Files.createTempFile(
            "vtchat-upload-",
            "." + ext
    );

    try {
        long bytes = Files.copy(
                content,
                temp,
                StandardCopyOption.REPLACE_EXISTING
        );

        if (request.contentLength() >= 0
                && bytes != request.contentLength()) {
            throw new IOException("Upload was incomplete");
        }

        Map<String, Object> options = ObjectUtils.asMap(
                "resource_type", kind.resourceType(),
                "type", kind.deliveryType(),
                "public_id", publicId,
                "overwrite", overwrite,
                "unique_filename", false,
                "use_filename", false
        );

        Map<?, ?> result;

        try {
            result = call(
                    "upload",
                    cfg.getUploadTimeoutMs(),
                    () -> cloudinary.uploader().upload(
                            temp.toFile(),
                            options
                    )
            );
        } catch (StorageException e) {
            if (e.kind() == StorageException.Kind.TIMEOUT
                    || e.kind() == StorageException.Kind.UNAVAILABLE) {
                cleanup(kind, publicId, ext);
            }
            throw e;
        }

        String returnedResourceType =
                text(result.get("resource_type"));
        String returnedDeliveryType =
                text(result.get("type"));
        String returnedPublicId =
                text(result.get("public_id"));
        String returnedFormat =
                text(result.get("format"));

        long reportedBytes =
                result.get("bytes") instanceof Number n
                        ? n.longValue()
                        : -1;

        Optional<CloudinaryRef> ref = CloudinaryRef.of(
                returnedResourceType,
                returnedDeliveryType,
                returnedPublicId,
                kind.raw()
                        ? null
                        : (returnedFormat == null
                                ? ext
                                : returnedFormat)
        );

        boolean publicIdMatches =
                publicId.equals(returnedPublicId);
        boolean invalidReference = ref.isEmpty();
        boolean byteCountMismatch =
                reportedBytes >= 0 && reportedBytes != bytes;

        if (invalidReference
                || !publicIdMatches
                || byteCountMismatch) {

            log.error(
                    "Cloudinary response validation failed: " +
                    "resourceType={}, deliveryType={}, format={}, " +
                    "expectedPublicId={}, returnedPublicId={}, " +
                    "publicIdMatches={}, referenceEmpty={}, " +
                    "expectedBytes={}, reportedBytes={}",
                    returnedResourceType,
                    returnedDeliveryType,
                    returnedFormat,
                    publicId,
                    returnedPublicId,
                    publicIdMatches,
                    invalidReference,
                    bytes,
                    reportedBytes
            );

            cleanup(kind, publicId, ext);

            throw new StorageException(
                    StorageException.Kind.FAILED,
                    "upload"
            );
        }

        log.info(
                "Cloudinary upload successful: resourceType={}, " +
                "extension={}, bytes={}",
                kind.resourceType(),
                ext,
                bytes
        );

        return ref.get().key();

    } finally {
        Files.deleteIfExists(temp);
    }
}


    private void cleanup(Kind kind, String publicId, String ext) {
        String format = kind.raw() ? null : ext;
        Optional<CloudinaryRef> ref = CloudinaryRef.of(kind.resourceType(), kind.deliveryType(), publicId, format);
        ref.ifPresent(r -> delete(r.key()));
    }

    private record Kind(String resourceType, String deliveryType) {
        boolean raw() {
            return CloudinaryRef.RAW.equals(resourceType);
        }
    }

    private static Kind kindFor(StoreRequest request, String ext) {
        String delivery = StorageKeys.AVATARS.equals(request.area()) ? CloudinaryRef.UPLOAD
                : CloudinaryRef.AUTHENTICATED;
        if (StorageKeys.AVATARS.equals(request.area())) {
            return new Kind(CloudinaryRef.IMAGE, delivery);
        }
        if (StorageKeys.KNOWLEDGE.equals(request.area())) {
            return new Kind(CloudinaryRef.RAW, delivery);
        }
        String mime = request.contentType() == null ? "" : request.contentType().toLowerCase(Locale.ROOT);
        if (mime.startsWith("image/")) {
            return new Kind(CloudinaryRef.IMAGE, delivery);
        }
        if (mime.startsWith("video/") || mime.startsWith("audio/")) {
            return new Kind(CloudinaryRef.VIDEO, delivery);
        }
        if (mime.isEmpty()) {
            if (ext.matches("jpg|jpeg|png|gif|webp"))
                return new Kind(CloudinaryRef.IMAGE, delivery);
            if (ext.matches("mp4|webm|mov|m4v|mp3|wav|ogg|m4a|aac|flac|opus"))
                return new Kind(CloudinaryRef.VIDEO, delivery);
        }
        return new Kind(CloudinaryRef.RAW, delivery);
    }

    @Override
    public Optional<Resource> retrieve(String key) {
        return CloudinaryRef.parse(key).flatMap(ref -> {
            String url = ref.isRaw() ? privateDownloadUrl(ref) : signedUrl(ref, null, ref.isPublic());
            return fetch(url);
        });
    }

    @Override
    public Optional<ObjectInfo> head(String key) {
        Optional<CloudinaryRef> parsed = CloudinaryRef.parse(key);
        if (parsed.isEmpty()) {
            return Optional.empty();
        }
        CloudinaryRef ref = parsed.get();
        try {
            Map<?, ?> info = call("resource", cfg.getApiTimeoutMs(),
                    () -> cloudinary.api().resource(ref.publicId(),
                            ObjectUtils.asMap("resource_type", ref.resourceType(), "type", ref.deliveryType())));
            long size = info.get("bytes") instanceof Number n ? n.longValue() : -1;
            return Optional.of(new ObjectInfo(size, null));
        } catch (StorageException e) {
            if (e.kind() == StorageException.Kind.NOT_FOUND) {
                return Optional.empty();
            }
            throw e;
        }
    }

    @Override
    public boolean delete(String key) {
        Optional<CloudinaryRef> parsed = CloudinaryRef.parse(key);
        if (parsed.isEmpty()) {
            return true;
        }
        CloudinaryRef ref = parsed.get();
        try {
            Map<?, ?> result = call("delete", cfg.getApiTimeoutMs(), () -> cloudinary.uploader().destroy(ref.publicId(),
                    ObjectUtils.asMap("resource_type", ref.resourceType(), "type", ref.deliveryType(), "invalidate",
                            true)));
            String outcome = text(result.get("result"));
            if ("ok".equals(outcome) || "not found".equals(outcome)) {
                return true;
            }
            orphans.record(PROVIDER, key, "FAILED");
            return false;
        } catch (StorageException e) {
            if (e.kind() == StorageException.Kind.NOT_FOUND) {
                return true;
            }
            orphans.record(PROVIDER, key, e.kind().name());
            return false;
        }
    }

    @Override
    public void storeThumbnail(String key, byte[] jpeg) {
    }

    @Override
    public Optional<Resource> retrieveThumbnail(String key) {
        return CloudinaryRef.parse(key).filter(CloudinaryRef::isImage).flatMap(r -> fetch(
                signedUrl(r, new Transformation().width(THUMB_SIZE).height(THUMB_SIZE).crop("limit"), r.isPublic())));
    }

    @Override
    public boolean thumbnailExists(String key) {
        return CloudinaryRef.parse(key).filter(CloudinaryRef::isImage).isPresent();
    }

    @Override
    public boolean presignable(String key) {
        return CloudinaryRef.parse(key).filter(r -> !r.isRaw()).isPresent();
    }

    @Override
    public Optional<String> presignGet(String key, Download download) {
        return CloudinaryRef.parse(key).filter(r -> !r.isRaw()).map(r -> {
            Transformation t = null;
            if (download != null && !download.inline()) {
                t = new Transformation().flags("attachment:" + attachmentName(download.fileName()));
            }
            return signedUrl(r, t, r.isPublic());
        });
    }

    @Override
    public Optional<String> presignThumbnailGet(String key) {
        return CloudinaryRef.parse(key).filter(CloudinaryRef::isImage)
                .map(r -> signedUrl(r, new Transformation().width(THUMB_SIZE).height(THUMB_SIZE).crop("limit"),
                        r.isPublic()));
    }

    @Override
    public long presignExpirySeconds() {
        return LINK_TTL_SECONDS;
    }

    @Override
    public Optional<String> publicUrl(String key) {
        return CloudinaryRef.parse(key).filter(CloudinaryRef::isPublic).map(r -> signedUrl(r, null, true));
    }

    private String signedUrl(CloudinaryRef ref, Transformation transformation, boolean unsigned) {
        var url = cloudinary.url().secure(true).resourceType(ref.resourceType()).type(ref.deliveryType())
                .signed(!unsigned);
        if (ref.format() != null) {
            url = url.format(ref.format());
        }
        if (transformation != null) {
            url = url.transformation(transformation);
        }
        return url.generate(ref.publicId());
    }

    private String privateDownloadUrl(CloudinaryRef ref) {
        long expires = System.currentTimeMillis() / 1000 + 60;

        try {
            return cloudinary.privateDownload(
                    ref.publicId(),
                    null,
                    ObjectUtils.asMap(
                            "resource_type", ref.resourceType(),
                            "type", ref.deliveryType(),
                            "expires_at", expires));
        } catch (Exception e) {
            throw new StorageException(
                    StorageException.Kind.FAILED,
                    "private download URL generation");
        }
    }

    static String attachmentName(String fileName) {
        String base = fileName == null ? "" : fileName;
        int dot = base.lastIndexOf('.');
        if (dot > 0) {
            base = base.substring(0, dot);
        }
        base = SAFE_NAME_CHARS.matcher(base).replaceAll("_").replaceAll("^_+|_+$", "");
        if (base.length() > 60) {
            base = base.substring(0, 60);
        }
        return base.isEmpty() ? "file" : base;
    }

    private Optional<Resource> fetch(String url) {
        count("fetch");
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofMillis(cfg.getDownloadTimeoutMs()))
                .GET()
                .build();
        HttpResponse<InputStream> response;
        try {
            response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
        } catch (HttpTimeoutException e) {
            throw failed("fetch", StorageException.Kind.TIMEOUT, e);
        } catch (IOException e) {
            throw failed("fetch",
                    LogSanitizer.isTimeout(e) ? StorageException.Kind.TIMEOUT : StorageException.Kind.UNAVAILABLE, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw failed("fetch", StorageException.Kind.UNAVAILABLE, e);
        }
        int status = response.statusCode();
        if (status == 200 || status == 206) {
            long length = response.headers().firstValueAsLong("content-length").orElse(-1);
            InputStream body = response.body();
            if (length >= 0) {
                return Optional.of(new Streamed(body, length));
            }
            try (body) {
                byte[] bytes = body.readNBytes((int) Math.min(UNKNOWN_LENGTH_CAP, Integer.MAX_VALUE));
                return Optional.of(new ByteArrayResource(bytes));
            } catch (IOException e) {
                throw failed("fetch", StorageException.Kind.UNAVAILABLE, e);
            }
        }
        closeQuietly(response.body());
        if (status == 404) {
            return Optional.empty();
        }
        StorageException.Kind kind = status == 401 || status == 403 ? StorageException.Kind.ACCESS_DENIED
                : status == 408 || status == 504 ? StorageException.Kind.TIMEOUT
                        : status == 420 || status == 429 || status >= 500 ? StorageException.Kind.UNAVAILABLE
                                : StorageException.Kind.FAILED;
        log.warn("Cloudinary fetch answered http status category={} op={}", kind, LogSanitizer.opId());
        throw new StorageException(kind, "fetch");
    }

    private <T> T call(String operation, long timeoutMs, Callable<T> body) {
        count(operation);
        Future<T> future;
        try {
            future = executor.submit(body);
        } catch (RejectedExecutionException e) {
            throw new StorageException(StorageException.Kind.UNAVAILABLE, operation);
        }
        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            future.cancel(true);
            log.warn("Cloudinary {} timed out op={}", operation, LogSanitizer.opId());
            throw new StorageException(StorageException.Kind.TIMEOUT, operation);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            future.cancel(true);
            throw new StorageException(StorageException.Kind.UNAVAILABLE, operation);
        } catch (ExecutionException e) {
            throw failed(operation, classify(e.getCause()), e.getCause());
        }
    }

    // private static StorageException failed(String operation, StorageException.Kind kind, Throwable cause) {
    //     if (kind != StorageException.Kind.NOT_FOUND) {
    //         log.warn("Cloudinary {} failed category={} cause={} op={}", operation, kind, LogSanitizer.describe(cause),
    //                 LogSanitizer.opId());
    //     }
    //     return new StorageException(kind, operation);
    // }

    private static StorageException failed(
        String operation,
        StorageException.Kind kind,
        Throwable cause
) {
    Throwable root = cause;

    // Find the deepest nested exception.
    for (int i = 0; root != null
            && root.getCause() != null
            && root.getCause() != root
            && i < 10; i++) {
        root = root.getCause();
    }

    log.warn(
        "Cloudinary failure: operation={}, category={}, " +
        "exceptionType={}, rootCauseType={}, op={}",
        operation,
        kind,
        cause == null ? "unknown" : cause.getClass().getName(),
        root == null ? "unknown" : root.getClass().getName(),
        LogSanitizer.opId()
    );

    // Log the exception chain without printing potentially sensitive messages.
    Throwable current = cause;
    for (int i = 0; current != null && i < 10; i++) {
        log.warn(
            "Cloudinary exception chain [{}]: {}",
            i,
            current.getClass().getName()
        );

        Throwable next = current.getCause();
        if (next == current) {
            break;
        }
        current = next;
    }
    if (cause != null) {
    StackTraceElement[] frames = cause.getStackTrace();

    for (int i = 0; i < Math.min(frames.length, 8); i++) {
        log.warn("Cloudinary failure stack [{}]: {}", i, frames[i]);
    }
}

log.warn(
        "Cloudinary SDK response error: operation={}, message={}",
        operation,
        safeCloudinaryError(cause)
);
    return new StorageException(kind, operation);
}

private static String safeCloudinaryError(Throwable error) {
    if (error == null || error.getMessage() == null) {
        return "No error message provided by Cloudinary SDK";
    }

    String message = error.getMessage();

    // Redact common credential fields and URLs before logging.
    message = message.replaceAll(
            "(?i)(api_key|api_secret|signature|authorization|token)"
                    + "\\s*[:=]\\s*[^,\\s}]+",
            "$1=[REDACTED]"
    );
    message = message.replaceAll(
            "(?i)https?://\\S+",
            "[URL_REDACTED]"
    );

    return message.substring(0, Math.min(message.length(), 500));
}

    static StorageException.Kind classify(Throwable t) {
        int depth = 0;
        for (Throwable c = t; c != null && depth < 8; c = c.getCause() == c ? null : c.getCause(), depth++) {
            String name = c.getClass().getSimpleName();
            if (name.contains("Timeout"))
                return StorageException.Kind.TIMEOUT;
            if (name.equals("NotFound"))
                return StorageException.Kind.NOT_FOUND;
            if (name.equals("AuthorizationRequired") || name.equals("NotAllowed"))
                return StorageException.Kind.ACCESS_DENIED;
            if (name.equals("RateLimited") || name.equals("GeneralError"))
                return StorageException.Kind.UNAVAILABLE;
            String m = c.getMessage() == null ? "" : c.getMessage().toLowerCase(Locale.ROOT);
            if (m.contains("timed out") || m.contains("timeout"))
                return StorageException.Kind.TIMEOUT;
            if (m.contains("invalid api_key") || m.contains("invalid api key") || m.contains("invalid signature")
                    || m.contains("unknown api key") || m.contains("must supply api_key")
                    || m.contains("invalid cloud_name")
                    || m.contains("cloud_name mismatch") || m.contains("disabled") || m.contains("unauthorized")) {
                return StorageException.Kind.ACCESS_DENIED;
            }
            if (m.contains("not found"))
                return StorageException.Kind.NOT_FOUND;
            if (m.contains("rate limit") || m.contains("too many requests"))
                return StorageException.Kind.UNAVAILABLE;
        }
        for (Throwable c = t; c != null && depth-- > 0; c = c.getCause() == c ? null : c.getCause()) {
            if (c instanceof IOException)
                return StorageException.Kind.UNAVAILABLE;
        }
        return t instanceof IOException ? StorageException.Kind.UNAVAILABLE : StorageException.Kind.FAILED;
    }

    private void count(String operation) {
        counters.computeIfAbsent(operation, k -> new LongAdder()).increment();
    }

    private static String text(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static void closeQuietly(InputStream in) {
        try {
            if (in != null)
                in.close();
        } catch (IOException ignored) {
        }
    }

    public void close() {
        executor.shutdown();
    }

    private static final class Streamed extends InputStreamResource {
        private final long length;

        Streamed(InputStream in, long length) {
            super(in);
            this.length = length;
        }

        @Override
        public long contentLength() {
            return length;
        }
    }
}
