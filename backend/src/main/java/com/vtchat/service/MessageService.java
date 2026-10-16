package com.vtchat.service;

import com.vtchat.dto.message.MessageResponse;
import com.vtchat.dto.message.MessageSearchResponse;
import com.vtchat.dto.message.SendMessageRequest;
import com.vtchat.entity.Conversation;
import com.vtchat.entity.Message;
import com.vtchat.entity.MessageType;
import com.vtchat.exception.ApiException;
import com.vtchat.repository.ConversationRepository;
import com.vtchat.repository.MessageDeletionRepository;
import com.vtchat.repository.MessageRepository;
import com.vtchat.repository.MessageTranslationRepository;
import com.vtchat.repository.UserRepository;
import com.vtchat.util.TextUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true, noRollbackFor = ApiException.class)
public class MessageService {

    public static final int MAX_CONTENT_LENGTH = 2000;
    public static final int HISTORY_LIMIT = 200;
    private static final int AROUND_HALF_WINDOW = 100;
    private static final int SEARCH_MIN = 2;
    private static final int SEARCH_MAX = 100;
    private static final int SEARCH_LIMIT = 50;

    private final MessageRepository messages;
    private final ConversationRepository conversations;
    private final MessageDeletionRepository deletions;
    private final UserRepository users;
    private final MessageAssembler assembler;
    private final MessageTranslationRepository translations;
    private final PresenceRegistry presence;
    private final ConversationStateService conversationStates;
    private final ConversationMetaCache conversationCache;
    private final ChatPermissionService permissions;

    public List<MessageResponse> history(Long conversationId, Long currentUserId, Long aroundMessageId) {
        requireParticipant(conversationId, currentUserId);
        List<Message> result;
        if (aroundMessageId == null) {
            result = new ArrayList<>(messages.findRecentVisible(conversationId, currentUserId, PageRequest.of(0, HISTORY_LIMIT)));
            Collections.reverse(result);
        } else {
            result = new ArrayList<>(messages.findVisibleUpTo(conversationId, currentUserId, aroundMessageId,
                    PageRequest.of(0, AROUND_HALF_WINDOW)));
            Collections.reverse(result);
            result.addAll(messages.findVisibleAfter(conversationId, currentUserId, aroundMessageId,
                    PageRequest.of(0, AROUND_HALF_WINDOW)));
        }
        return assembler.assemble(result, currentUserId);
    }

    public List<MessageSearchResponse> search(Long conversationId, Long currentUserId, String rawQuery) {
        requireParticipant(conversationId, currentUserId);
        String query = rawQuery == null ? "" : rawQuery.strip();
        if (query.length() < SEARCH_MIN) {
            throw ApiException.badRequest("QUERY_TOO_SHORT", "Type at least " + SEARCH_MIN + " characters to search");
        }
        if (query.length() > SEARCH_MAX) {
            throw ApiException.badRequest("QUERY_TOO_LONG", "Search text is too long");
        }
        return messages.searchVisible(conversationId, currentUserId, TextUtil.likeContains(query), PageRequest.of(0, SEARCH_LIMIT))
                .stream()
                .map(m -> new MessageSearchResponse(m.getId(), conversationId, m.getSender().getId(),
                        m.getSender().getUsername(), m.getContent(), m.getMessageType(), m.getMediaName(),
                        m.getCreatedAt()))
                .toList();
    }

    public void assertCanWrite(Long conversationId, Long userId) {
        requireWritable(conversationId, userId);
    }

    public Message requireMessageForWrite(Long messageId, Long userId) {
        Message message = requireMessage(messageId, userId);
        permissions.requireMessagingAllowed(message.getConversation());
        return message;
    }

    private Conversation requireWritable(Long conversationId, Long userId) {
        Conversation conversation = requireParticipant(conversationId, userId);
        permissions.requireMessagingAllowed(conversation);
        return conversation;
    }

    public void assertParticipant(Long conversationId, Long userId) {
        requireParticipant(conversationId, userId);
    }

    public Message requireMessage(Long messageId, Long userId) {
        if (messageId == null) {
            throw validation("Message is required");
        }
        Message message = messages.findWithConversationById(messageId)
                .orElseThrow(() -> ApiException.notFound("MESSAGE_NOT_FOUND", "Message not found"));
        if (!message.getConversation().includes(userId)) {
            throw ApiException.forbidden("FORBIDDEN", "You do not have access to this conversation");
        }
        return message;
    }

    @Transactional(noRollbackFor = ApiException.class)
    public MessageResponse send(Long senderId, SendMessageRequest request) {
        if (request == null || request.conversationId() == null) {
            throw validation("Conversation is required");
        }
        String content = validContent(request.content());
        Conversation conversation = requireWritable(request.conversationId(), senderId);

        Message replyTo = resolveReply(request.replyToMessageId(), conversation);

        Message saved = save(Message.builder().conversation(conversation).content(content).replyToMessage(replyTo), senderId);
        return assembler.assemble(saved, null);
    }

    public record StoredMedia(MessageType type, String key, String name, long size, String mime, Integer duration) {
    }

    @Transactional(noRollbackFor = ApiException.class)
    public MessageResponse sendMedia(Long senderId, Long conversationId, StoredMedia media, String rawCaption, Long replyToId) {
        Conversation conversation = requireWritable(conversationId, senderId);
        String caption = optionalCaption(rawCaption);
        Message replyTo = resolveReply(replyToId, conversation);
        Message saved = save(Message.builder()
                .conversation(conversation)
                .content(caption)
                .replyToMessage(replyTo)
                .messageType(media.type())
                .mediaKey(media.key())
                .mediaName(media.name())
                .mediaSize(media.size())
                .mimeType(media.mime())
                .duration(media.duration()), senderId);
        return assembler.assemble(saved, null);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public MessageResponse edit(Long userId, Long messageId, String rawContent) {
        Message message = requireMessageForWrite(messageId, userId);
        if (!message.getSender().getId().equals(userId)) {
            throw ApiException.forbidden("CANNOT_EDIT", "You cannot edit this message");
        }
        if (message.isDeleted()) {
            throw ApiException.conflict("MESSAGE_DELETED", "This message was deleted");
        }
        if (message.getMessageType() != MessageType.TEXT) {
            throw validation("Only text messages can be edited");
        }
        String content = validContent(rawContent);
        if (!content.equals(message.getContent())) {
            message.setContent(content);
            message.setEdited(true);
            message.setUpdatedAt(Instant.now());
            messages.save(message);
            translations.deleteByMessageId(message.getId());
        }
        return assembler.assemble(message, null);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public MessageResponse deleteForEveryone(Long userId, Long messageId) {
        Message message = requireMessageForWrite(messageId, userId);
        if (!message.getSender().getId().equals(userId)) {
            throw ApiException.forbidden("CANNOT_DELETE_FOR_EVERYONE", "You cannot delete this message for everyone");
        }
        if (!message.isDeleted()) {
            message.setDeleted(true);
            message.setDeletedAt(Instant.now());
            messages.save(message);
            translations.deleteByMessageId(message.getId());
        }
        return assembler.assemble(message, null);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public MessageResponse forward(Long userId, Long messageId, Long destinationConversationId) {
        if (destinationConversationId == null) {
            throw validation("Choose a conversation to forward to");
        }
        Message source = requireMessageForWrite(messageId, userId);
        if (source.isDeleted()) {
            throw ApiException.conflict("MESSAGE_DELETED", "A deleted message cannot be forwarded");
        }
        if (deletions.isHidden(messageId, userId)) {
            throw ApiException.notFound("MESSAGE_NOT_FOUND", "Message not found");
        }
        if (source.getMessageType().isCall()) {
            throw validation("Call records cannot be forwarded");
        }
        Conversation destination = requireWritable(destinationConversationId, userId);
        Message saved = save(Message.builder()
                .conversation(destination)
                .content(source.getContent())
                .forwarded(true)
                .forwardedFromMessageId(source.getId())
                .messageType(source.getMessageType())
                .mediaKey(source.getMediaKey())
                .mediaName(source.getMediaName())
                .mediaSize(source.getMediaSize())
                .mimeType(source.getMimeType())
                .duration(source.getDuration()), userId);
        return assembler.assemble(saved, null);
    }

    @Transactional(noRollbackFor = ApiException.class)
    public MessageResponse recordCall(Long conversationId, Long callerId, MessageType type, String callId,
                                      String callStatus, Integer durationSeconds) {
        if (!type.isCall()) {
            throw new IllegalArgumentException("Not a call type");
        }
        Conversation conversation = requireWritable(conversationId, callerId);
        Message saved = save(Message.builder()
                .conversation(conversation)
                .content("")
                .messageType(type)
                .callId(callId)
                .callStatus(callStatus)
                .duration(durationSeconds), callerId);
        return assembler.assemble(saved, null);
    }

    public Conversation requireConversationMember(Long conversationId, Long userId) {
        return requireParticipant(conversationId, userId);
    }

    private Message save(Message.MessageBuilder builder, Long senderId) {
        Message draft = builder.sender(users.getReferenceById(senderId)).build();
        Long receiverId = draft.getConversation().otherParticipant(senderId).getId();
        draft.setDeliveryStatus(presence.isConnected(receiverId)
                ? com.vtchat.entity.DeliveryStatus.DELIVERED : com.vtchat.entity.DeliveryStatus.SENT);
        Message saved = messages.saveAndFlush(draft);
        boolean countsAsUnread = !saved.getMessageType().isCall() || "MISSED".equals(saved.getCallStatus());
        conversationStates.onIncoming(saved.getConversation().getId(), receiverId, countsAsUnread);
        Conversation conversation = saved.getConversation();
        conversation.setUpdatedAt(saved.getCreatedAt());
        conversations.save(conversation);
        conversationCache.invalidate(conversation.getId());
        return saved;
    }

    private Message resolveReply(Long replyToId, Conversation conversation) {
        if (replyToId == null) {
            return null;
        }
        Message replyTo = messages.findById(replyToId)
                .orElseThrow(() -> ApiException.notFound("MESSAGE_NOT_FOUND", "The message you are replying to was not found"));
        if (!replyTo.getConversation().getId().equals(conversation.getId())) {
            throw validation("You can only reply to a message from this conversation");
        }
        if (replyTo.isDeleted()) {
            throw ApiException.conflict("MESSAGE_DELETED", "You cannot reply to a deleted message");
        }
        if (replyTo.getMessageType().isCall()) {
            throw validation("You cannot reply to a call record");
        }
        return replyTo;
    }

    private static String optionalCaption(String raw) {
        String caption = normalize(raw);
        if (caption.codePointCount(0, caption.length()) > MAX_CONTENT_LENGTH) {
            throw validation("Caption is too long (maximum " + MAX_CONTENT_LENGTH + " characters)");
        }
        return caption;
    }

    private Conversation requireParticipant(Long conversationId, Long userId) {
        Conversation conversation = conversations.findWithUsersById(conversationId)
                .orElseThrow(() -> ApiException.notFound("CONVERSATION_NOT_FOUND", "Conversation not found"));
        if (!conversation.includes(userId)) {
            throw ApiException.forbidden("FORBIDDEN", "You do not have access to this conversation");
        }
        return conversation;
    }

    private static String validContent(String raw) {
        String content = normalize(raw);
        if (content.isEmpty()) {
            throw validation("Message cannot be empty");
        }
        if (content.codePointCount(0, content.length()) > MAX_CONTENT_LENGTH) {
            throw validation("Message is too long (maximum " + MAX_CONTENT_LENGTH + " characters)");
        }
        return content;
    }

    static String normalize(String raw) {
        if (raw == null) return "";
        return raw.replace("\r\n", "\n").replace('\r', '\n')
                .replaceAll("[\\p{Cc}&&[^\\n\\t]]", "")
                .strip();
    }

    static ApiException validation(String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "VALIDATION_ERROR", message);
    }
}
