package com.vtchat.service;

import com.vtchat.entity.Message;
import com.vtchat.exception.ApiException;
import com.vtchat.repository.MessageDeletionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(noRollbackFor = ApiException.class)
public class MessageDeletionService {

    public record Hidden(Long conversationId, Long messageId) {
    }

    private final MessageService messageService;
    private final MessageDeletionRepository deletions;

    public Hidden deleteForMe(Long userId, Long messageId) {
        Message message = messageService.requireMessage(messageId, userId);
        deletions.hide(messageId, userId);
        return new Hidden(message.getConversation().getId(), messageId);
    }
}
