package com.vtchat.service;

import com.vtchat.dto.message.ReceiptEvent;
import com.vtchat.entity.Conversation;
import com.vtchat.entity.Message;
import com.vtchat.exception.ApiException;
import com.vtchat.repository.MessageRepository;
import com.vtchat.websocket.MessageEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DeliveryService {

    private final MessageEventPublisher publisher;
    private final DeliveryTx tx;

    public void deliverPendingTo(Long userId) {
        List<ReceiptEvent> events;
        try {
            events = tx.deliverPending(userId);
        } catch (RuntimeException e) {
            log.warn("Could not mark pending messages as delivered for user {}", userId, e);
            return;
        }
        events.forEach(publisher::receipt);
    }

    public ReceiptEvent markRead(Long userId, Long conversationId, Long lastReadMessageId) {
        if (conversationId == null || lastReadMessageId == null) {
            throw MessageService.validation("Conversation and message are required");
        }
        ReceiptEvent event = tx.markRead(userId, conversationId, lastReadMessageId);
        if (event != null) {
            publisher.receipt(event);
        }
        return event;
    }

    @Service
    @RequiredArgsConstructor
    public static class DeliveryTx {

        private final MessageRepository messages;
        private final MessageService messageService;

        @Transactional
        public List<ReceiptEvent> deliverPending(Long userId) {
            List<ReceiptEvent> events = new java.util.ArrayList<>();
            for (Object[] row : messages.findPendingDelivery(userId)) {
                Long conversationId = (Long) row[0];
                Long upTo = (Long) row[1];
                if (messages.markDelivered(conversationId, userId, upTo) > 0) {
                    events.add(ReceiptEvent.delivered(conversationId, userId, upTo));
                }
            }
            return events;
        }

        @Transactional
        public ReceiptEvent markRead(Long userId, Long conversationId, Long lastReadMessageId) {
            Conversation conversation = messageService.requireConversationMember(conversationId, userId);
            Message watermark = messages.findById(lastReadMessageId)
                    .orElseThrow(() -> ApiException.notFound("MESSAGE_NOT_FOUND", "Message not found"));
            if (!watermark.getConversation().getId().equals(conversation.getId())) {
                throw ApiException.forbidden("FORBIDDEN", "That message is not part of this conversation");
            }
            List<Long> ids = messages.findUnreadIncomingIds(conversationId, userId, lastReadMessageId);
            if (ids.isEmpty()) {
                return null;
            }
            messages.insertReceipts(ids, userId, Instant.now());
            messages.markRead(ids);
            return ReceiptEvent.read(conversationId, userId, lastReadMessageId);
        }
    }
}
