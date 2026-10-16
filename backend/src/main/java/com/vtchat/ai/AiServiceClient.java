package com.vtchat.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtchat.config.AiProperties;
import com.vtchat.config.RagProperties;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;

@Slf4j
@Component
public class AiServiceClient {

    public static final String API_KEY_HEADER = "X-Internal-Api-Key";
    public static final String REQUEST_ID_HEADER = "X-Request-Id";

    public record HistoryItem(String role, String content) {
    }

    public record ChatPayload(String message, String conversationId, List<HistoryItem> history) {
    }

    public record ChatResult(String response, String model) {
    }

    public record RagSource(Long documentId, String fileName, Integer pageNumber) {
    }

    public record RagPayload(String message, String conversationId, List<HistoryItem> history, long userId, List<Long> documentIds) {
    }

    public record RagResult(String response, String model, boolean grounded, List<RagSource> sources) {
    }

    public record RagStreamEnd(String model, boolean grounded, List<RagSource> sources) {
    }

    public record ProcessResult(int chunkCount, int pageCount) {
    }

    private final AiProperties props;
    private final ObjectMapper mapper;
    private final int ragProcessTimeoutMs;

    public AiServiceClient(AiProperties props, ObjectMapper mapper, RagProperties ragProps) {
        this.props = props;
        this.mapper = mapper;
        this.ragProcessTimeoutMs = ragProps.getProcessTimeoutMs();
    }

    @PostConstruct
    void init() {
        System.setProperty("sun.net.http.retryPost", "false");
        if (props.getService().getApiKey() == null || props.getService().getApiKey().isBlank()) {
            log.warn("AI_SERVICE_API_KEY is not set: AI requests will be refused by the AI service");
        }
    }

    public ChatResult chat(ChatPayload payload, String requestId) {
        HttpURLConnection conn = null;
        try {
            conn = open("/ai/chat", payload, requestId, false);
            int status = conn.getResponseCode();
            if (status >= 400) {
                throw fromError(status, readAll(conn.getErrorStream()));
            }
            JsonNode body = mapper.readTree(conn.getInputStream());
            String text = body.path("response").asText(null);
            if (text == null || text.isBlank()) {
                throw new AiServiceException(AiServiceException.Kind.UNAVAILABLE, "empty response from the AI service");
            }
            return new ChatResult(text, body.path("model").asText(""));
        } catch (IOException e) {
            throw fromIo(e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public String stream(ChatPayload payload, String requestId, Consumer<String> onDelta) {
        HttpURLConnection conn = null;
        try {
            conn = open("/ai/chat/stream", payload, requestId, true);
            int status = conn.getResponseCode();
            if (status >= 400) {
                throw fromError(status, readAll(conn.getErrorStream()));
            }
            long deadline = System.currentTimeMillis() + props.getStreamMaxMs();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String event = null;
                StringBuilder data = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (System.currentTimeMillis() > deadline) {
                        throw new AiServiceException(AiServiceException.Kind.TIMEOUT, "stream exceeded ai.stream-max-ms");
                    }
                    if (line.isEmpty()) {
                        if (event != null) {
                            JsonNode node = data.length() == 0 ? mapper.createObjectNode() : mapper.readTree(data.toString());
                            switch (event) {
                                case "delta" -> {
                                    String text = node.path("text").asText("");
                                    if (!text.isEmpty()) {
                                        onDelta.accept(text);
                                    }
                                }
                                case "done" -> {
                                    return node.path("model").asText("");
                                }
                                case "error" -> throw map(node.path("code").asText(""), node.path("message").asText(""), 0);
                                default -> {   }
                            }
                        }
                        event = null;
                        data.setLength(0);
                    } else if (line.startsWith("event:")) {
                        event = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        data.append(line.substring(5).trim());
                    }
                }
            }
            throw new AiServiceException(AiServiceException.Kind.UNAVAILABLE, "AI stream ended without a done event");
        } catch (IOException e) {
            throw fromIo(e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public ProcessResult processDocument(long userId, long documentId, int version, String fileType, String fileName,
                                         InputStream data, long length, String requestId) {
        HttpURLConnection conn = null;
        try {
            String q = "?userId=" + userId + "&documentId=" + documentId + "&version=" + version
                    + "&fileType=" + java.net.URLEncoder.encode(fileType, StandardCharsets.UTF_8)
                    + "&fileName=" + java.net.URLEncoder.encode(fileName, StandardCharsets.UTF_8);
            AiProperties.Service s = props.getService();
            conn = (HttpURLConnection) URI.create(s.getUrl().replaceAll("/+$", "") + "/rag/documents/process" + q).toURL().openConnection();
            conn.setConnectTimeout(s.getConnectTimeoutMs());
            conn.setReadTimeout(ragProcessTimeoutMs);
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("Content-Type", "application/octet-stream");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("Connection", "close");
            conn.setRequestProperty(API_KEY_HEADER, s.getApiKey() == null ? "" : s.getApiKey());
            if (requestId != null) {
                conn.setRequestProperty(REQUEST_ID_HEADER, requestId);
            }
            conn.setFixedLengthStreamingMode(length);
            try (OutputStream out = conn.getOutputStream()) {
                data.transferTo(out);
            }
            int status = conn.getResponseCode();
            if (status >= 400) {
                throw fromError(status, readAll(conn.getErrorStream()));
            }
            JsonNode body = mapper.readTree(conn.getInputStream());
            return new ProcessResult(body.path("chunkCount").asInt(0), body.path("pageCount").asInt(0));
        } catch (IOException e) {
            throw fromIo(e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public void deleteDocumentVectors(long userId, long documentId, String requestId) {
        HttpURLConnection conn = null;
        try {
            AiProperties.Service s = props.getService();
            conn = (HttpURLConnection) URI.create(s.getUrl().replaceAll("/+$", "") + "/rag/documents/" + documentId + "?userId=" + userId).toURL().openConnection();
            conn.setConnectTimeout(s.getConnectTimeoutMs());
            conn.setReadTimeout(s.getTimeoutMs());
            conn.setRequestMethod("DELETE");
            conn.setInstanceFollowRedirects(false);
            conn.setRequestProperty("Connection", "close");
            conn.setRequestProperty(API_KEY_HEADER, s.getApiKey() == null ? "" : s.getApiKey());
            if (requestId != null) {
                conn.setRequestProperty(REQUEST_ID_HEADER, requestId);
            }
            int status = conn.getResponseCode();
            if (status >= 400) {
                throw fromError(status, readAll(conn.getErrorStream()));
            }
            readAll(conn.getInputStream());
        } catch (IOException e) {
            throw fromIo(e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public RagResult ragChat(RagPayload payload, String requestId) {
        HttpURLConnection conn = null;
        try {
            conn = open("/rag/chat", payload, requestId, false);
            int status = conn.getResponseCode();
            if (status >= 400) {
                throw fromError(status, readAll(conn.getErrorStream()));
            }
            JsonNode body = mapper.readTree(conn.getInputStream());
            String text = body.path("response").asText(null);
            if (text == null || text.isBlank()) {
                throw new AiServiceException(AiServiceException.Kind.UNAVAILABLE, "empty response from the AI service");
            }
            return new RagResult(text, body.path("model").asText(""), body.path("grounded").asBoolean(false), sources(body.path("sources")));
        } catch (IOException e) {
            throw fromIo(e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public RagStreamEnd ragStream(RagPayload payload, String requestId, Consumer<String> onDelta) {
        HttpURLConnection conn = null;
        try {
            conn = open("/rag/chat/stream", payload, requestId, true);
            int status = conn.getResponseCode();
            if (status >= 400) {
                throw fromError(status, readAll(conn.getErrorStream()));
            }
            long deadline = System.currentTimeMillis() + props.getStreamMaxMs();
            List<RagSource> sources = List.of();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String event = null;
                StringBuilder data = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    if (System.currentTimeMillis() > deadline) {
                        throw new AiServiceException(AiServiceException.Kind.TIMEOUT, "stream exceeded ai.stream-max-ms");
                    }
                    if (line.isEmpty()) {
                        if (event != null) {
                            JsonNode node = data.length() == 0 ? mapper.createObjectNode() : mapper.readTree(data.toString());
                            switch (event) {
                                case "delta" -> {
                                    String text = node.path("text").asText("");
                                    if (!text.isEmpty()) {
                                        onDelta.accept(text);
                                    }
                                }
                                case "sources" -> sources = sources(node.path("sources"));
                                case "done" -> {
                                    return new RagStreamEnd(node.path("model").asText(""), node.path("grounded").asBoolean(false), sources);
                                }
                                case "error" -> throw map(node.path("code").asText(""), node.path("message").asText(""), 0);
                                default -> {   }
                            }
                        }
                        event = null;
                        data.setLength(0);
                    } else if (line.startsWith("event:")) {
                        event = line.substring(6).trim();
                    } else if (line.startsWith("data:")) {
                        data.append(line.substring(5).trim());
                    }
                }
            }
            throw new AiServiceException(AiServiceException.Kind.UNAVAILABLE, "AI stream ended without a done event");
        } catch (IOException e) {
            throw fromIo(e);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private static List<RagSource> sources(JsonNode array) {
        List<RagSource> out = new java.util.ArrayList<>();
        if (array != null && array.isArray()) {
            for (JsonNode n : array) {
                if (n.hasNonNull("documentId")) {
                    out.add(new RagSource(n.get("documentId").asLong(), n.path("fileName").asText(""),
                            n.hasNonNull("pageNumber") ? n.get("pageNumber").asInt() : null));
                }
            }
        }
        return out;
    }

    private HttpURLConnection open(String path, Object payload, String requestId, boolean streaming) throws IOException {
        return open(path, payload, requestId, streaming, props.getService().getTimeoutMs());
    }

    private HttpURLConnection open(String path, Object payload, String requestId, boolean streaming, int readTimeoutMs) throws IOException {
        AiProperties.Service s = props.getService();
        HttpURLConnection conn = (HttpURLConnection) URI.create(s.getUrl().replaceAll("/+$", "") + path).toURL().openConnection();
        conn.setConnectTimeout(s.getConnectTimeoutMs());
        conn.setReadTimeout(readTimeoutMs);
        conn.setRequestMethod("POST");
        conn.setDoOutput(true);
        conn.setInstanceFollowRedirects(false);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Accept", streaming ? "text/event-stream" : "application/json");
        conn.setRequestProperty("Connection", "close");
        conn.setRequestProperty(API_KEY_HEADER, s.getApiKey() == null ? "" : s.getApiKey());
        if (requestId != null) {
            conn.setRequestProperty(REQUEST_ID_HEADER, requestId);
        }
        byte[] body = mapper.writeValueAsBytes(payload);
        conn.setFixedLengthStreamingMode(body.length);
        try (OutputStream out = conn.getOutputStream()) {
            out.write(body);
        }
        return conn;
    }

    private String readAll(InputStream in) throws IOException {
        if (in == null) {
            return "";
        }
        try (in) {
            byte[] bytes = in.readNBytes(16 * 1024);
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private AiServiceException fromError(int status, String body) {
        String code = "";
        int retry = 0;
        try {
            JsonNode n = mapper.readTree(body);
            code = n.path("code").asText("");
            retry = n.path("retryAfterSeconds").asInt(0);
        } catch (Exception ignored) {
        }
        AiServiceException e = map(code, "HTTP " + status, retry);
        if (code.isEmpty()) {
            return new AiServiceException(status == 504 ? AiServiceException.Kind.TIMEOUT : AiServiceException.Kind.UNAVAILABLE, "AI service HTTP " + status);
        }
        return e;
    }

    private AiServiceException map(String code, String detail, int retryAfter) {
        return switch (code) {
            case "LLM_TIMEOUT" -> new AiServiceException(AiServiceException.Kind.TIMEOUT, "LLM timeout");
            case "LLM_RATE_LIMITED" -> new AiServiceException(AiServiceException.Kind.BUSY, "provider rate limit", retryAfter > 0 ? retryAfter : 30);
            case "CONTEXT_TOO_LARGE" -> new AiServiceException(AiServiceException.Kind.CONTEXT_TOO_LARGE, "context too large");
            case "MESSAGE_TOO_LONG", "INVALID_REQUEST" -> new AiServiceException(AiServiceException.Kind.INVALID, "AI service rejected the request (" + code + ")");
            case "UNAUTHORIZED", "LLM_AUTH" -> {
                log.error("AI service/LLM rejected our credentials ({}). Check AI_SERVICE_API_KEY and LLM_API_KEY.", code);
                yield new AiServiceException(AiServiceException.Kind.UNAVAILABLE, code);
            }
            case "RAG_UNAVAILABLE", "RAG_DISABLED", "RAG_BUSY", "VECTOR_STORE_MISMATCH", "VECTOR_STORE_FAILED", "RAG_INIT_FAILED",
                 "EMBEDDING_FAILED", "EMBEDDING_DIMENSION_MISMATCH" ->
                    new AiServiceException(AiServiceException.Kind.RAG_UNAVAILABLE, "RAG backend problem (" + code + ")", retryAfter, code);
            case "NO_TEXT", "EMPTY_DOCUMENT", "DOCUMENT_TOO_LARGE", "UNSUPPORTED_TYPE", "UNSUPPORTED_CONTENT", "UNSUPPORTED_DOC",
                 "ENCRYPTED_PDF", "TOO_MANY_PAGES", "CORRUPT_DOCUMENT", "PROCESSING_FAILED" ->
                    new AiServiceException(AiServiceException.Kind.DOCUMENT_REJECTED, "document rejected (" + code + ")", 0, code);
            case "QUERY_TOO_LONG", "EMPTY_QUERY" -> new AiServiceException(AiServiceException.Kind.INVALID, "AI service rejected the query (" + code + ")");
            default -> new AiServiceException(AiServiceException.Kind.UNAVAILABLE, code.isEmpty() ? detail : code);
        };
    }

    private AiServiceException fromIo(IOException e) {
        if (e instanceof SocketTimeoutException) {
            return new AiServiceException(AiServiceException.Kind.TIMEOUT, "AI service timed out");
        }
        return new AiServiceException(AiServiceException.Kind.UNAVAILABLE, "AI service not reachable (" + e.getClass().getSimpleName() + ")");
    }
}
