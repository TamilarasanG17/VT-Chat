package com.vtchat.service;

import com.vtchat.ai.AiServiceClient.HistoryItem;
import com.vtchat.config.AiProperties;
import com.vtchat.dto.ai.AiDtos.ConversationResponse;
import com.vtchat.dto.ai.AiDtos.MessageResponse;
import com.vtchat.dto.ai.AiDtos.SourceResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vtchat.entity.AiConversation;
import com.vtchat.entity.AiMessage;
import com.vtchat.entity.AiMessageRole;
import com.vtchat.exception.ApiException;
import com.vtchat.repository.AiConversationRepository;
import com.vtchat.repository.AiMessageRepository;
import com.vtchat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class AiConversationStore {

    public static final String DEFAULT_TITLE = "New chat";
    public static final String MODE_NORMAL = "NORMAL";
    public static final String MODE_RAG = "RAG";

    private final AiConversationRepository conversations;
    private final AiMessageRepository messages;
    private final UserRepository users;
    private final AiProperties props;
    private final ObjectMapper mapper;

    public record Begun(MessageResponse userMessage, List<HistoryItem> history) {
    }

    public record Saved(MessageResponse assistantMessage, ConversationResponse conversation) {
    }

    @Transactional
    public ConversationResponse create(Long userId, String title) {
        if (conversations.countByUser(userId) >= props.getMaxConversationsPerUser()) {
            throw ApiException.conflict("AI_CONVERSATION_LIMIT",
                    "You have reached the limit of " + props.getMaxConversationsPerUser()
                            + " AI chats. Delete one to start a new chat.");
        }
        Instant now = Instant.now();
        String clean = title == null ? "" : title.trim().replaceAll("\\s+", " ");
        AiConversation c = conversations.save(AiConversation.builder()
                .user(users.getReferenceById(userId))
                .title(clean.isEmpty() ? DEFAULT_TITLE : clean)
                .createdAt(now)
                .updatedAt(now)
                .build());
        return toResponse(c);
    }

    @Transactional(readOnly = true)
    public List<ConversationResponse> list(Long userId) {
        return conversations.findByUser(
                userId,
                PageRequest.of(0, Math.max(1, props.getMaxConversationsPerUser()))).stream()
                .map(AiConversationStore::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ConversationResponse get(Long userId, Long id) {
        return toResponse(owned(userId, id));
    }

    @Transactional(readOnly = true)
    public List<MessageResponse> messages(Long userId, Long id, Long before, int limit) {
        owned(userId, id);
        List<AiMessage> newest = new ArrayList<>(
                messages.findNewest(id, before, PageRequest.of(0, Math.max(1, Math.min(limit, 200)))));
        Collections.reverse(newest);
        return newest.stream().map(this::toResponse).toList();
    }

    @Transactional
    public void delete(Long userId, Long id) {
        owned(userId, id);
        messages.deleteByConversation(id);
        conversations.deleteOwned(id, userId);
    }

    @Transactional
    public Begun begin(Long userId, Long id, String text) {
        return begin(userId, id, text, MODE_NORMAL);
    }

    @Transactional
    public Begun begin(Long userId, Long id, String text, String mode) {
        AiConversation c = owned(userId, id);
        int n = props.getMaxContextMessages();
        List<AiMessage> recent = n > 0 ? new ArrayList<>(messages.findNewest(id, null, PageRequest.of(0, n)))
                : new ArrayList<>();
        Collections.reverse(recent);
        List<HistoryItem> history = recent.stream()
                .map(m -> new HistoryItem(m.getRole().name().toLowerCase(), m.getContent())).toList();

        Instant now = Instant.now();
        AiMessage saved = messages.save(AiMessage.builder().conversation(c).role(AiMessageRole.USER).content(text)
                .mode(mode).createdAt(now).build());
        if (DEFAULT_TITLE.equals(c.getTitle())) {
            conversations.rename(id, titleFrom(text), now);
        } else {
            conversations.touch(id, now);
        }
        return new Begun(toResponse(saved), history);
    }

    @Transactional
    public Optional<Saved> saveAssistant(Long userId, Long id, String text) {
        return saveAssistant(userId, id, text, MODE_NORMAL, List.of());
    }

    @Transactional
    public Optional<Saved> saveAssistant(Long userId, Long id, String text, String mode, List<SourceResponse> sources) {
        Optional<AiConversation> found = conversations.findOwned(id, userId);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        AiConversation conversation = found.get();
        AiMessage saved = messages.save(AiMessage.builder().conversation(conversation).role(AiMessageRole.ASSISTANT)
                .content(text).mode(mode).sources(writeSources(sources)).createdAt(now).build());
        conversation.setUpdatedAt(now);
        return Optional.of(new Saved(toResponse(saved), toResponse(conversation)));
    }

    private AiConversation owned(Long userId, Long id) {
        return conversations.findOwned(id, userId)
                .orElseThrow(() -> ApiException.notFound("AI_CONVERSATION_NOT_FOUND", "AI conversation not found"));
    }

    static String titleFrom(String text) {
        String t = text.replaceAll("[`*_#>\\[\\]]", " ").replaceAll("\\s+", " ").trim();
        if (t.isEmpty()) {
            return DEFAULT_TITLE;
        }
        if (t.length() <= 60) {
            return t;
        }
        int cut = t.lastIndexOf(' ', 60);
        return t.substring(0, cut > 20 ? cut : 60).trim() + "…";
    }

    static ConversationResponse toResponse(AiConversation c) {
        return new ConversationResponse(c.getId(), c.getTitle(), c.getCreatedAt(), c.getUpdatedAt());
    }

    MessageResponse toResponse(AiMessage m) {
        return new MessageResponse(m.getId(), m.getRole().name(), m.getContent(), m.getCreatedAt(),
                m.getMode() == null ? MODE_NORMAL : m.getMode(), readSources(m.getSources()));
    }

    private String writeSources(List<SourceResponse> sources) {
        if (sources == null || sources.isEmpty()) {
            return null;
        }
        try {
            return mapper.writeValueAsString(sources);
        } catch (Exception e) {
            return null;
        }
    }

    private List<SourceResponse> readSources(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, new TypeReference<List<SourceResponse>>() {
            });
        } catch (Exception e) {
            return null;
        }
    }
}
