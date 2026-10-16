package com.vtchat.service;

import com.vtchat.dto.conversation.ConversationStateEvent;
import com.vtchat.dto.message.MessageResponse;
import com.vtchat.dto.notification.NotificationEvent;
import com.vtchat.entity.NotificationType;
import com.vtchat.websocket.NotificationEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDispatcher {

    private final NotificationService service;
    private final NotificationEventPublisher publisher;

    public void afterMessageSaved(MessageResponse message) {
        try {
            service.onMessage(message).ifPresent(outcome -> {
                publisher.conversationState(outcome.receiverId(), ConversationStateEvent.of(outcome.state()));
                if (outcome.notification() != null) {
                    push(outcome.notification());
                }
            });
        } catch (RuntimeException e) {
            log.warn("Could not create the message notification: {}", e.getClass().getSimpleName());
        }
    }

    public void onIncomingCall(String callId, Long callerId, Long receiverId) {
        try {
            service.forCall(NotificationType.INCOMING_CALL, callId, callerId, receiverId).ifPresent(this::push);
        } catch (RuntimeException e) {
            log.warn("Could not create the incoming-call notification: {}", e.getClass().getSimpleName());
        }
    }

    public void onCallFinished(String callId, Long callerId, Long receiverId, boolean missed) {
        try {
            long unread = service.closeIncomingCall(callId, receiverId);
            if (unread >= 0) {
                publisher.notification(receiverId, NotificationEvent.updated(unread, null));
            }
            if (missed) {
                service.forCall(NotificationType.MISSED_CALL, callId, callerId, receiverId).ifPresent(this::push);
            }
        } catch (RuntimeException e) {
            log.warn("Could not update call notifications: {}", e.getClass().getSimpleName());
        }
    }

    public void onChatRequestReceived(Long receiverId, Long senderId, Long requestId) {
        try {
            service.forChatRequest(NotificationType.CHAT_REQUEST, receiverId, senderId, requestId, null).ifPresent(this::push);
        } catch (RuntimeException e) {
            log.warn("Could not create the chat-request notification: {}", e.getClass().getSimpleName());
        }
    }

    public void onChatRequestAnswered(Long receiverId, Long senderId, Long requestId, boolean accepted, Long conversationId) {
        try {
            long unread = service.closeChatRequest(receiverId, requestId);
            if (unread >= 0) {
                publisher.notification(receiverId, NotificationEvent.updated(unread, null));
            }
            if (accepted) {
                service.forChatRequest(NotificationType.CHAT_REQUEST_ACCEPTED, senderId, receiverId, requestId, conversationId).ifPresent(this::push);
            }
        } catch (RuntimeException e) {
            log.warn("Could not update chat-request notifications: {}", e.getClass().getSimpleName());
        }
    }

    public void onConnectionEnded(Long recipientId, Long actorId, Long conversationId) {
        try {
            service.forConnectionEnded(recipientId, conversationId).ifPresent(this::push);
        } catch (RuntimeException e) {
            log.warn("Could not create the connection-ended notification: {}", e.getClass().getSimpleName());
        }
    }

    private void push(NotificationService.Created created) {
        publisher.notification(created.recipientId(), NotificationEvent.created(created.notification(), created.unreadCount()));
    }
}
