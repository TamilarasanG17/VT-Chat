package com.vtchat.service;

import com.vtchat.dto.message.ReactionResponse;
import com.vtchat.entity.Message;
import com.vtchat.exception.ApiException;
import com.vtchat.repository.MessageReactionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(noRollbackFor = ApiException.class)
public class MessageReactionService {

    private static final Map<String, String> ALLOWED = new LinkedHashMap<>();

    static {
        for (String emoji : new String[]{"👍", "❤️", "😂", "😮", "😢", "🙏"}) {
            ALLOWED.put(strip(emoji), emoji);
        }
    }

    public record ReactionState(Long conversationId, Long messageId, List<ReactionResponse> reactions) {
    }

    private final MessageService messageService;
    private final MessageReactionRepository reactions;

    public ReactionState react(Long userId, Long messageId, String rawReaction) {
        String reaction = ALLOWED.get(strip(rawReaction));
        if (reaction == null) {
            throw MessageService.validation("That reaction is not supported");
        }
        Message message = messageService.requireMessageForWrite(messageId, userId);
        if (message.isDeleted()) {
            throw ApiException.conflict("MESSAGE_DELETED", "You cannot react to a deleted message");
        }
        if (message.getMessageType().isCall()) {
            throw MessageService.validation("You cannot react to a call record");
        }
        reactions.upsert(messageId, userId, reaction);
        return state(message);
    }

    public ReactionState unreact(Long userId, Long messageId) {
        Message message = messageService.requireMessageForWrite(messageId, userId);
        reactions.deleteByMessageAndUser(messageId, userId);
        return state(message);
    }

    private ReactionState state(Message message) {
        List<ReactionResponse> current = message.isDeleted() ? List.of()
                : reactions.findByMessageIds(List.of(message.getId())).stream().map(ReactionResponse::from).toList();
        return new ReactionState(message.getConversation().getId(), message.getId(), current);
    }

    private static String strip(String emoji) {
        return emoji == null ? "" : emoji.replace("️", "").strip();
    }
}
