package com.vtchat.service;

import com.vtchat.config.TranslationProperties;
import com.vtchat.dto.translation.LanguageResponse;
import com.vtchat.dto.translation.TranslationResponse;
import com.vtchat.entity.Message;
import com.vtchat.entity.MessageTranslation;
import com.vtchat.entity.MessageType;
import com.vtchat.exception.ApiException;
import com.vtchat.repository.MessageDeletionRepository;
import com.vtchat.repository.MessageRepository;
import com.vtchat.repository.MessageTranslationRepository;
import com.vtchat.translation.TranslationServiceClient;
import com.vtchat.translation.TranslationServiceException;
import com.vtchat.util.HashUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranslationServiceImpl implements TranslationService {

    static final String GENERIC_FAILURE = "Unable to translate this message right now.";
    static final String UNAVAILABLE_MESSAGE = "Translation service is temporarily unavailable.";

    private final TranslationProperties props;
    private final TranslationServiceClient client;
    private final MessageService messageService;
    private final MessageRepository messages;
    private final MessageDeletionRepository deletions;
    private final MessageTranslationRepository translations;

    private final ConcurrentMap<String, CompletableFuture<TranslationResponse>> inFlight = new ConcurrentHashMap<>();

    @Override
    public TranslationResponse translateMessage(Long userId, Long messageId, String rawTarget) {
        String target = requireSupported(rawTarget);

        Message message = messageService.requireMessage(messageId, userId);
        if (message.isDeleted()) {
            throw ApiException.conflict("MESSAGE_DELETED", "This message was deleted");
        }
        if (deletions.isHidden(messageId, userId)) {
            throw ApiException.notFound("MESSAGE_NOT_FOUND", "Message not found");
        }
        if (message.getMessageType() != MessageType.TEXT) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NOT_TRANSLATABLE", "Only text messages can be translated");
        }
        String text = message.getContent() == null ? "" : message.getContent().strip();
        if (text.isEmpty()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "EMPTY_MESSAGE", "There is nothing to translate");
        }
        if (text.codePointCount(0, text.length()) > props.getMaxTextLength()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TEXT_TOO_LONG", "This message is too long to translate");
        }

        String hash = HashUtil.sha256Hex(text);
        TranslationResponse cached = cached(messageId, target, hash, text);
        if (cached != null) {
            return cached;
        }
        log.info("Translation requested: message={} target={} chars={}", messageId, target, text.length());
        return deduplicated(messageId + ":" + target + ":" + hash, () -> translateAndSave(messageId, target, text, hash));
    }

    private TranslationResponse cached(Long messageId, String target, String hash, String text) {
        return translations.findByMessageIdAndTargetLanguage(messageId, target)
                .filter(t -> hash.equals(t.getSourceHash()))
                .map(t -> toResponse(messageId, t, text))
                .orElse(null);
    }

    private TranslationResponse translateAndSave(Long messageId, String target, String text, String hash) {
        TranslationServiceClient.Result result = translateOrFail(text, target);

        Message fresh = messages.findById(messageId).orElse(null);
        if (fresh == null || fresh.isDeleted() || !hash.equals(HashUtil.sha256Hex(fresh.getContent().strip()))) {
            throw ApiException.conflict("MESSAGE_CHANGED", "This message was changed. Please try again.");
        }

        MessageTranslation row = translations.findByMessageIdAndTargetLanguage(messageId, target)
                .orElseGet(() -> MessageTranslation.builder().message(fresh).targetLanguage(target).build());
        row.setSourceLanguage(result.sourceLanguage());
        row.setSourceScript(result.sourceScript());
        row.setRomanized(result.romanized());
        row.setTranslatedText(result.translatedText());
        row.setSourceHash(hash);
        try {
            translations.save(row);
        } catch (DataIntegrityViolationException e) {
            row = translations.findByMessageIdAndTargetLanguage(messageId, target).orElse(row);
        }
        return toResponse(messageId, row, text);
    }

    private static TranslationResponse toResponse(Long messageId, MessageTranslation t, String originalText) {
        return new TranslationResponse(messageId, t.getSourceLanguage(), t.getSourceScript(), t.isRomanized(),
                t.getTargetLanguage(), originalText, t.getTranslatedText());
    }

    private TranslationServiceClient.Result translateOrFail(String text, String target) {
        try {
            TranslationServiceClient.Result result = client.translate(text, target);
            log.info("Translation succeeded: target={}", target);
            return result;
        } catch (TranslationServiceException e) {
            switch (e.getKind()) {
                case TIMEOUT -> log.warn("Translation timed out: target={}", target);
                case UNAVAILABLE -> log.warn("Translation service unavailable: {}", e.getMessage());
                default -> log.error("Translation failed ({}): {}", e.getKind(), e.getMessage());
            }
            throw switch (e.getKind()) {
                case TIMEOUT -> new ApiException(HttpStatus.GATEWAY_TIMEOUT, "TRANSLATION_TIMEOUT", GENERIC_FAILURE);
                case UNAVAILABLE, AUTH -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "TRANSLATION_UNAVAILABLE", UNAVAILABLE_MESSAGE);
                case REJECTED -> new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "TRANSLATION_NOT_SUPPORTED", "This message can't be translated.");
                default -> new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "TRANSLATION_FAILED", GENERIC_FAILURE);
            };
        }
    }

    @Override
    public List<LanguageResponse> supportedLanguages() {
        return props.getLanguages().entrySet().stream()
                .map(e -> new LanguageResponse(e.getKey(), e.getValue()))
                .toList();
    }

    private String requireSupported(String raw) {
        String code = raw == null ? "" : raw.strip().toLowerCase(Locale.ROOT);
        if (code.isEmpty()) {
            throw ApiException.badRequest("INVALID_TARGET_LANGUAGE", "Please choose a language to translate to");
        }
        if (!props.getLanguages().containsKey(code)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "UNSUPPORTED_LANGUAGE", "That language is not supported");
        }
        return code;
    }

    private static ApiException unavailable(String code) {
        return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, code, GENERIC_FAILURE);
    }

    private TranslationResponse deduplicated(String key, java.util.function.Supplier<TranslationResponse> work) {
        CompletableFuture<TranslationResponse> mine = new CompletableFuture<>();
        CompletableFuture<TranslationResponse> existing = inFlight.putIfAbsent(key, mine);
        if (existing != null) {
            return await(existing);
        }
        try {
            TranslationResponse response = work.get();
            mine.complete(response);
            return response;
        } catch (RuntimeException e) {
            mine.completeExceptionally(e);
            throw e;
        } finally {
            inFlight.remove(key, mine);
        }
    }

    private TranslationResponse await(CompletableFuture<TranslationResponse> future) {
        try {
            return future.get(props.getService().getTimeoutMs() * 3L + 5000L, TimeUnit.MILLISECONDS);
        } catch (ExecutionException e) {
            if (e.getCause() instanceof RuntimeException re) {
                throw re;
            }
            throw unavailable("TRANSLATION_FAILED");
        } catch (TimeoutException e) {
            throw new ApiException(HttpStatus.GATEWAY_TIMEOUT, "TRANSLATION_TIMEOUT", GENERIC_FAILURE);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw unavailable("TRANSLATION_FAILED");
        }
    }
}
