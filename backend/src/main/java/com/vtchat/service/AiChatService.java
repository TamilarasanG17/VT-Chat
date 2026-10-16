package com.vtchat.service;

import com.vtchat.ai.AiServiceClient;
import com.vtchat.ai.AiServiceClient.ChatPayload;
import com.vtchat.ai.AiServiceException;
import com.vtchat.ai.StreamAbortedException;
import com.vtchat.ai.AiServiceClient.RagPayload;
import com.vtchat.ai.AiServiceClient.RagResult;
import com.vtchat.ai.AiServiceClient.RagStreamEnd;
import com.vtchat.ai.AiServiceClient.RagSource;
import com.vtchat.config.AiProperties;
import com.vtchat.config.RagProperties;
import com.vtchat.dto.ai.AiDtos.SourceResponse;
import com.vtchat.config.RequestIdFilter;
import com.vtchat.dto.ai.AiDtos.ChatResponse;
import com.vtchat.dto.ai.AiDtos.MessageResponse;
import com.vtchat.dto.ai.AiDtos.SendMessageResponse;
import com.vtchat.exception.ApiException;
import com.vtchat.redis.RedisKeyService;
import com.vtchat.redis.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiChatService {

    private final AiConversationStore store;
    private final AiServiceClient client;
    private final AiProperties props;
    private final RedisService redis;
    private final RedisKeyService keys;
    private final RagProperties ragProps;
    private final KnowledgeService knowledge;

    private final Set<String> generating = ConcurrentHashMap.newKeySet();

    public interface StreamSink {
        void start(MessageResponse userMessage);

        void delta(String text);

        void done(SendMessageResponse result);
    }

    public ChatResponse chat(Long userId, String rawMessage) {
        String text = validate(rawMessage);
        String rid = MDC.get(RequestIdFilter.MDC_KEY);
        log.info("AI request received userId={} chars={} mode=oneshot", userId, text.length());
        try {
            AiServiceClient.ChatResult r = client.chat(new ChatPayload(text, null, List.of()), rid);
            log.info("AI request completed userId={} mode=oneshot", userId);
            return new ChatResponse(r.response(), r.model());
        } catch (AiServiceException e) {
            throw toApi(userId, e);
        }
    }

    public SendMessageResponse sendMessage(Long userId, Long conversationId, String rawMessage) {
        String text = validate(rawMessage);
        String rid = MDC.get(RequestIdFilter.MDC_KEY);
        String lock = acquire(userId, conversationId);
        try {
            AiConversationStore.Begun begun = store.begin(userId, conversationId, text);
            log.info("AI request received userId={} conversationId={} chars={} history={}", userId, conversationId, text.length(), begun.history().size());
            AiServiceClient.ChatResult result;
            try {
                result = client.chat(new ChatPayload(text, String.valueOf(conversationId), begun.history()), rid);
            } catch (AiServiceException e) {
                throw toApi(userId, e);
            }
            AiConversationStore.Saved saved = store.saveAssistant(userId, conversationId, result.response())
                    .orElseThrow(() -> ApiException.notFound("AI_CONVERSATION_NOT_FOUND", "AI conversation not found"));
            log.info("AI request completed userId={} conversationId={}", userId, conversationId);
            return new SendMessageResponse(begun.userMessage(), saved.assistantMessage(), saved.conversation(), result.model());
        } finally {
            release(lock, userId, conversationId);
        }
    }

    public void streamMessage(Long userId, Long conversationId, String rawMessage, StreamSink sink) {
        String text = validate(rawMessage);
        String rid = MDC.get(RequestIdFilter.MDC_KEY);
        String lock = acquire(userId, conversationId);
        try {
            AiConversationStore.Begun begun = store.begin(userId, conversationId, text);
            log.info("AI request received userId={} conversationId={} chars={} history={} mode=stream", userId, conversationId, text.length(), begun.history().size());
            StringBuilder answer = new StringBuilder();
            boolean[] started = {false};
            String model;
            try {
                model = client.stream(new ChatPayload(text, String.valueOf(conversationId), begun.history()), rid, delta -> {
                    if (!started[0]) {
                        started[0] = true;
                        sink.start(begun.userMessage());
                    }
                    answer.append(delta);
                    sink.delta(delta);
                });
            } catch (StreamAbortedException e) {
                log.info("AI stream cancelled by the client userId={} conversationId={} chars={}", userId, conversationId, answer.length());
                if (!answer.toString().isBlank()) {
                    store.saveAssistant(userId, conversationId, answer.toString());
                }
                return;
            } catch (AiServiceException e) {
                throw toApi(userId, e);
            }
            AiConversationStore.Saved saved = store.saveAssistant(userId, conversationId, answer.toString())
                    .orElseThrow(() -> ApiException.notFound("AI_CONVERSATION_NOT_FOUND", "AI conversation not found"));
            log.info("AI request completed userId={} conversationId={} chars={} mode=stream", userId, conversationId, answer.length());
            try {
                sink.done(new SendMessageResponse(begun.userMessage(), saved.assistantMessage(), saved.conversation(), model));
            } catch (StreamAbortedException ignored) {
            }
        } finally {
            release(lock, userId, conversationId);
        }
    }

    public SendMessageResponse sendRag(Long userId, Long conversationId, String rawMessage, List<Long> selected) {
        String text = validateRag(rawMessage);
        String rid = MDC.get(RequestIdFilter.MDC_KEY);
        String lock = acquire(userId, conversationId);
        try {
            Map<Long, String> docs = readyOrFail(userId, selected);
            AiConversationStore.Begun begun = store.begin(userId, conversationId, text, AiConversationStore.MODE_RAG);
            log.info("RAG request received userId={} conversationId={} chars={} documents={} history={}", userId, conversationId, text.length(), docs.size(), begun.history().size());
            RagResult result;
            try {
                result = client.ragChat(new RagPayload(text, String.valueOf(conversationId), begun.history(), userId, List.copyOf(docs.keySet())), rid);
            } catch (AiServiceException e) {
                throw toApi(userId, e);
            }
            List<SourceResponse> sources = result.grounded() ? checked(result.sources(), docs) : List.of();
            AiConversationStore.Saved saved = store.saveAssistant(userId, conversationId, result.response(), AiConversationStore.MODE_RAG, sources)
                    .orElseThrow(() -> ApiException.notFound("AI_CONVERSATION_NOT_FOUND", "AI conversation not found"));
            log.info("RAG request completed userId={} conversationId={} grounded={} sources={}", userId, conversationId, result.grounded(), sources.size());
            return new SendMessageResponse(begun.userMessage(), saved.assistantMessage(), saved.conversation(), result.model());
        } finally {
            release(lock, userId, conversationId);
        }
    }

    public void streamRag(Long userId, Long conversationId, String rawMessage, List<Long> selected, StreamSink sink) {
        String text = validateRag(rawMessage);
        String rid = MDC.get(RequestIdFilter.MDC_KEY);
        String lock = acquire(userId, conversationId);
        try {
            Map<Long, String> docs = readyOrFail(userId, selected);
            AiConversationStore.Begun begun = store.begin(userId, conversationId, text, AiConversationStore.MODE_RAG);
            log.info("RAG request received userId={} conversationId={} chars={} documents={} history={} mode=stream", userId, conversationId, text.length(), docs.size(), begun.history().size());
            StringBuilder answer = new StringBuilder();
            boolean[] started = {false};
            RagStreamEnd end;
            try {
                end = client.ragStream(new RagPayload(text, String.valueOf(conversationId), begun.history(), userId, List.copyOf(docs.keySet())), rid, delta -> {
                    if (!started[0]) {
                        started[0] = true;
                        sink.start(begun.userMessage());
                    }
                    answer.append(delta);
                    sink.delta(delta);
                });
            } catch (StreamAbortedException e) {
                log.info("RAG stream cancelled by the client userId={} conversationId={} chars={}", userId, conversationId, answer.length());
                if (!answer.toString().isBlank()) {
                    store.saveAssistant(userId, conversationId, answer.toString(), AiConversationStore.MODE_RAG, List.of());
                }
                return;
            } catch (AiServiceException e) {
                throw toApi(userId, e);
            }
            List<SourceResponse> sources = end.grounded() ? checked(end.sources(), docs) : List.of();
            AiConversationStore.Saved saved = store.saveAssistant(userId, conversationId, answer.toString(), AiConversationStore.MODE_RAG, sources)
                    .orElseThrow(() -> ApiException.notFound("AI_CONVERSATION_NOT_FOUND", "AI conversation not found"));
            log.info("RAG request completed userId={} conversationId={} grounded={} sources={} mode=stream", userId, conversationId, end.grounded(), sources.size());
            try {
                sink.done(new SendMessageResponse(begun.userMessage(), saved.assistantMessage(), saved.conversation(), end.model()));
            } catch (StreamAbortedException ignored) {
            }
        } finally {
            release(lock, userId, conversationId);
        }
    }

    private String validateRag(String raw) {
        String text = validate(raw);
        if (text.length() > ragProps.getMaxQueryLength()) {
            throw ApiException.badRequest("RAG_QUERY_TOO_LONG",
                    "Question is too long (maximum " + ragProps.getMaxQueryLength() + " characters)");
        }
        if (!ragProps.isEnabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "KNOWLEDGE_DISABLED", "My Knowledge is turned off on this server.");
        }
        return text;
    }

    private Map<Long, String> readyOrFail(Long userId, List<Long> selected) {
        Map<Long, String> docs = knowledge.readyDocuments(userId, selected);
        if (docs.isEmpty()) {
            throw ApiException.conflict("NO_READY_DOCUMENTS",
                    "You have no ready documents to search. Upload a document in My Knowledge and wait until it is Ready.");
        }
        return docs;
    }

    private static List<SourceResponse> checked(List<RagSource> reported, Map<Long, String> docs) {
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        java.util.ArrayList<SourceResponse> out = new java.util.ArrayList<>();
        for (RagSource r : reported) {
            String name = docs.get(r.documentId());
            if (name != null && seen.add(r.documentId() + ":" + r.pageNumber())) {
                out.add(new SourceResponse(r.documentId(), name, r.pageNumber()));
            }
        }
        return out;
    }

    String validate(String raw) {
        String text = raw == null ? "" : raw.strip();
        if (text.isEmpty()) {
            throw ApiException.badRequest("AI_INVALID_REQUEST", "Message must not be empty");
        }
        if (text.length() > props.getMaxMessageLength()) {
            throw ApiException.badRequest("AI_MESSAGE_TOO_LONG",
                    "Message is too long (maximum " + props.getMaxMessageLength() + " characters)");
        }
        return text;
    }

    private String acquire(Long userId, Long conversationId) {
        String local = userId + ":" + conversationId;
        if (!generating.add(local)) {
            throw busy();
        }
        Optional<Boolean> got = redis.setIfAbsent(keys.aiGenerating(userId, conversationId), "1",
                Duration.ofMillis((long) props.getStreamMaxMs() + props.getService().getTimeoutMs() + 5000L));
        if (got.isPresent() && !got.get()) {
            generating.remove(local);
            throw busy();
        }
        return local;
    }

    private void release(String local, Long userId, Long conversationId) {
        generating.remove(local);
        redis.delete(keys.aiGenerating(userId, conversationId));
    }

    private static ApiException busy() {
        return new ApiException(HttpStatus.CONFLICT, "AI_GENERATION_IN_PROGRESS", "Please wait for the current answer to finish.");
    }

    private ApiException toApi(Long userId, AiServiceException e) {
        log.warn("AI request failed userId={} kind={} detail={}", userId, e.kind(), e.getMessage());
        return switch (e.kind()) {
            case TIMEOUT -> new ApiException(HttpStatus.GATEWAY_TIMEOUT, "AI_TIMEOUT", "The AI took too long to respond. Please try again.");
            case BUSY -> new ApiException(HttpStatus.TOO_MANY_REQUESTS, "AI_BUSY", "The AI is busy right now. Please try again shortly.", Math.max(1, e.retryAfterSeconds()));
            case CONTEXT_TOO_LARGE -> new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "AI_CONTEXT_TOO_LARGE", "This conversation is too long for the AI. Please start a new chat.");
            case INVALID -> ApiException.badRequest("AI_INVALID_REQUEST", "That message could not be sent to the AI.");
            case UNAVAILABLE -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "AI_UNAVAILABLE", "AI service is temporarily unavailable. Please try again later.");
            case RAG_UNAVAILABLE -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "RAG_UNAVAILABLE",
                    "Searching your documents is temporarily unavailable. You can still chat in Normal AI mode.");
            case DOCUMENT_REJECTED -> ApiException.badRequest("AI_INVALID_REQUEST", "That question could not be answered from your documents.");
        };
    }
}
