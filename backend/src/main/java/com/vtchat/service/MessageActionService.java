package com.vtchat.service;

import com.vtchat.dto.conversation.ConversationStateResponse;
import com.vtchat.dto.message.DeleteMessageResponse;
import com.vtchat.dto.message.DeleteScope;
import com.vtchat.dto.message.MessageEvent;
import com.vtchat.dto.message.MessageResponse;
import com.vtchat.dto.message.ReactionResponse;
import com.vtchat.dto.message.SendMessageRequest;
import com.vtchat.websocket.MessageEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Service
@RequiredArgsConstructor
public class MessageActionService {

    private final MessageService messageService;
    private final MessageReactionService reactionService;
    private final MessageDeletionService deletionService;
    private final MediaService mediaService;
    private final VoiceMessageService voiceService;
    private final MessageEventPublisher publisher;
    private final TypingService typing;
    private final DeliveryService delivery;
    private final ConversationActionService conversationActions;
    private final NotificationDispatcher notifier;

    public MessageResponse send(Long userId, SendMessageRequest request) {
        MessageResponse saved = messageService.send(userId, request);
        publisher.toConversation(MessageEvent.created(saved));
        typing.stopForSend(userId, saved.conversationId());
        notifier.afterMessageSaved(saved);
        return saved;
    }

    public MessageResponse sendMedia(Long userId, Long conversationId, MultipartFile file, String caption, Long replyToId) {
        MessageResponse saved = mediaService.upload(userId, conversationId, file, caption, replyToId);
        publisher.toConversation(MessageEvent.created(saved));
        typing.stopForSend(userId, saved.conversationId());
        notifier.afterMessageSaved(saved);
        return saved;
    }

    public MessageResponse sendVoice(Long userId, Long conversationId, MultipartFile file, Double duration, Long replyToId) {
        MessageResponse saved = voiceService.upload(userId, conversationId, file, duration, replyToId);
        publisher.toConversation(MessageEvent.created(saved));
        typing.stopForSend(userId, saved.conversationId());
        notifier.afterMessageSaved(saved);
        return saved;
    }

    public ConversationStateResponse markRead(Long userId, Long conversationId, Long lastReadMessageId) {
        return conversationActions.markRead(userId, conversationId, lastReadMessageId);
    }

    public MessageResponse edit(Long userId, Long messageId, String content) {
        MessageResponse updated = messageService.edit(userId, messageId, content);
        publisher.toConversation(MessageEvent.updated(updated));
        return updated;
    }

    public List<ReactionResponse> react(Long userId, Long messageId, String reaction) {
        return publish(reactionService.react(userId, messageId, reaction));
    }

    public List<ReactionResponse> unreact(Long userId, Long messageId) {
        return publish(reactionService.unreact(userId, messageId));
    }

    public DeleteMessageResponse delete(Long userId, Long messageId, String rawScope) {
        return switch (DeleteScope.parse(rawScope)) {
            case ME -> deleteForMe(userId, messageId);
            case EVERYONE -> deleteForEveryone(userId, messageId);
        };
    }

    public DeleteMessageResponse deleteForMe(Long userId, Long messageId) {
        MessageDeletionService.Hidden hidden = deletionService.deleteForMe(userId, messageId);
        publisher.toUser(userId, MessageEvent.deletedForMe(hidden.conversationId(), hidden.messageId()));
        return new DeleteMessageResponse(messageId, DeleteScope.ME.name(), null);
    }

    public DeleteMessageResponse deleteForEveryone(Long userId, Long messageId) {
        MessageResponse tombstone = messageService.deleteForEveryone(userId, messageId);
        publisher.toConversation(MessageEvent.deletedForEveryone(tombstone));
        mediaService.cleanupIfUnreferenced(messageId);
        return new DeleteMessageResponse(messageId, DeleteScope.EVERYONE.name(), tombstone);
    }

    public MessageResponse forward(Long userId, Long messageId, Long destinationConversationId) {
        MessageResponse forwarded = messageService.forward(userId, messageId, destinationConversationId);
        publisher.toConversation(MessageEvent.created(forwarded));
        notifier.afterMessageSaved(forwarded);
        return forwarded;
    }

    private List<ReactionResponse> publish(MessageReactionService.ReactionState state) {
        publisher.toConversation(MessageEvent.reactionUpdated(state.conversationId(), state.messageId(), state.reactions()));
        return state.reactions();
    }
}
