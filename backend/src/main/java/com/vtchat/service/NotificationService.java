package com.vtchat.service;

import com.vtchat.dto.conversation.ConversationStateResponse;
import com.vtchat.dto.message.MessageResponse;
import com.vtchat.dto.notification.NotificationPageResponse;
import com.vtchat.dto.notification.NotificationResponse;
import com.vtchat.entity.CallSession;
import com.vtchat.entity.CallType;
import com.vtchat.entity.Conversation;
import com.vtchat.entity.MessageType;
import com.vtchat.entity.Notification;
import com.vtchat.entity.NotificationSettings;
import com.vtchat.entity.NotificationType;
import com.vtchat.entity.User;
import com.vtchat.exception.ApiException;
import com.vtchat.repository.CallSessionRepository;
import com.vtchat.repository.ConversationParticipantStateRepository;
import com.vtchat.repository.ConversationRepository;
import com.vtchat.repository.MessageRepository;
import com.vtchat.repository.NotificationRepository;
import com.vtchat.repository.UserRepository;
import com.vtchat.util.TextUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@RequiredArgsConstructor
@Transactional(noRollbackFor = ApiException.class)
public class NotificationService {

    private static final int MAX_PAGE_SIZE = 50;
    private static final int PREVIEW_LENGTH = 100;

    public record Created(Long recipientId, NotificationResponse notification, long unreadCount) {
    }

    public record MessageOutcome(Long receiverId, ConversationStateResponse state, Created notification) {
    }

    private final NotificationRepository notifications;
    private final NotificationSettingsService settingsService;
    private final ConversationRepository conversations;
    private final ConversationParticipantStateRepository states;
    private final ConversationStateService stateService;
    private final MessageRepository messages;
    private final CallSessionRepository calls;
    private final UserRepository users;
    private final ActiveChatRegistry activeChats;
    private final UnreadCounterService counters;

    public Optional<MessageOutcome> onMessage(MessageResponse m) {
        if (m == null || m.deleted()) {
            return Optional.empty();
        }
        Conversation c = conversations.findWithUsersById(m.conversationId()).orElse(null);
        if (c == null || !c.includes(m.senderId())) {
            return Optional.empty();
        }
        User receiver = c.otherParticipant(m.senderId());
        User sender = c.otherParticipant(receiver.getId());
        ConversationStateResponse state = stateService.snapshot(receiver.getId(), c.getId());

        Created created = null;
        if (!m.messageType().isCall() && wantsMessageNotification(receiver.getId(), c.getId(), state)
                && !notifications.existsByRecipientIdAndTypeAndMessageReferenceId(receiver.getId(), NotificationType.NEW_MESSAGE, m.id())) {
            NotificationSettings settings = settingsService.get(receiver.getId());
            String body = settings.isMessagePreview() ? preview(m) : "You have a new message";
            Notification saved = notifications.saveAndFlush(Notification.builder()
                    .recipient(receiver)
                    .type(NotificationType.NEW_MESSAGE)
                    .title("New message from " + sender.getUsername())
                    .message(body)
                    .conversation(c)
                    .messageReference(messages.getReferenceById(m.id()))
                    .build());
            counters.notificationCreated(receiver.getId());
            created = new Created(receiver.getId(), NotificationResponse.of(saved), notifications.countUnread(receiver.getId()));
        }
        return Optional.of(new MessageOutcome(receiver.getId(), state, created));
    }

    private boolean wantsMessageNotification(Long receiverId, Long conversationId, ConversationStateResponse state) {
        return settingsService.get(receiverId).isMessageNotifications()
                && !state.muted()
                && !activeChats.isActive(receiverId, conversationId);
    }

    public Optional<Created> forCall(NotificationType type, String callId, Long callerId, Long receiverId) {
        CallSession call = calls.findByCallId(callId).orElse(null);
        if (call == null) {
            return Optional.empty();
        }
        NotificationSettings settings = settingsService.get(receiverId);
        boolean wanted = type == NotificationType.MISSED_CALL ? settings.isMissedCallNotifications() : settings.isIncomingCallNotifications();
        if (!wanted || notifications.existsByRecipientIdAndTypeAndCallReferenceId(receiverId, type, call.getId())) {
            return Optional.empty();
        }
        User caller = users.findById(callerId).orElse(null);
        if (caller == null) {
            return Optional.empty();
        }
        String kind = call.getCallType() == CallType.VIDEO ? "video" : "voice";
        Notification saved = notifications.saveAndFlush(Notification.builder()
                .recipient(users.getReferenceById(receiverId))
                .type(type)
                .title((type == NotificationType.MISSED_CALL ? "Missed " : "Incoming ") + kind + " call")
                .message("From " + caller.getUsername())
                .conversation(call.getConversation())
                .callReference(call)
                .build());
        counters.notificationCreated(receiverId);
        return Optional.of(new Created(receiverId, NotificationResponse.of(saved), notifications.countUnread(receiverId)));
    }

    public Optional<Created> forChatRequest(NotificationType type, Long recipientId, Long actorId, Long requestId, Long conversationId) {
        if (!settingsService.get(recipientId).isMessageNotifications()
                || notifications.existsByRecipientIdAndTypeAndChatRequestId(recipientId, type, requestId)) {
            return Optional.empty();
        }
        User actor = users.findById(actorId).orElse(null);
        if (actor == null) {
            return Optional.empty();
        }
        boolean accepted = type == NotificationType.CHAT_REQUEST_ACCEPTED;
        Notification saved = notifications.saveAndFlush(Notification.builder()
                .recipient(users.getReferenceById(recipientId))
                .type(type)
                .title(accepted ? "Chat request accepted" : "New chat request")
                .message(accepted ? actor.getUsername() + " accepted your request. You can chat now."
                        : actor.getUsername() + " wants to chat with you")
                .conversation(conversationId == null ? null : conversations.getReferenceById(conversationId))
                .chatRequestId(requestId)
                .build());
        counters.notificationCreated(recipientId);
        return Optional.of(new Created(recipientId, NotificationResponse.of(saved), notifications.countUnread(recipientId)));
    }

    public Optional<Created> forConnectionEnded(Long recipientId, Long conversationId) {
        Notification saved = notifications.saveAndFlush(Notification.builder()
                .recipient(users.getReferenceById(recipientId))
                .type(NotificationType.SYSTEM)
                .title("Connection ended")
                .message("Your connection with this user has ended. You can no longer send messages until a new chat request is accepted.")
                .conversation(conversationId == null ? null : conversations.getReferenceById(conversationId))
                .build());
        counters.notificationCreated(recipientId);
        return Optional.of(new Created(recipientId, NotificationResponse.of(saved), notifications.countUnread(recipientId)));
    }

    public long closeChatRequest(Long recipientId, Long requestId) {
        if (notifications.markChatRequestRead(recipientId, requestId) == 0) {
            return -1;
        }
        counters.notificationsChanged(recipientId);
        return notifications.countUnread(recipientId);
    }

    public long closeIncomingCall(String callId, Long receiverId) {
        CallSession call = calls.findByCallId(callId).orElse(null);
        if (call == null || notifications.markCallRead(receiverId, NotificationType.INCOMING_CALL, call.getId()) == 0) {
            return -1;
        }
        counters.notificationsChanged(receiverId);
        return notifications.countUnread(receiverId);
    }

    @Transactional(readOnly = true, noRollbackFor = ApiException.class)
    public NotificationPageResponse page(Long userId, int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        Page<Notification> result = notifications.findPage(userId, PageRequest.of(Math.max(page, 0), safeSize));
        return new NotificationPageResponse(result.getContent().stream().map(NotificationResponse::of).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.hasNext(),
                counters.notificationCount(userId, () -> notifications.countUnread(userId)));
    }

    @Transactional(readOnly = true, noRollbackFor = ApiException.class)
    public long unreadCount(Long userId) {
        return counters.notificationCount(userId, () -> notifications.countUnread(userId));
    }

    public long markRead(Long userId, Long notificationId) {
        notifications.findOwned(notificationId, userId).orElseThrow(NotificationService::notFound);
        notifications.markRead(notificationId, userId);
        counters.notificationsChanged(userId);
        return notifications.countUnread(userId);
    }

    public long markAllRead(Long userId) {
        notifications.markAllRead(userId);
        counters.notificationsChanged(userId);
        return 0;
    }

    public long delete(Long userId, Long notificationId) {
        if (notifications.deleteOwned(notificationId, userId) == 0) {
            throw notFound();
        }
        counters.notificationsChanged(userId);
        return notifications.countUnread(userId);
    }

    static String preview(MessageResponse m) {
        String caption = m.content() == null ? "" : m.content().strip();
        MessageType type = m.messageType();
        String label = switch (type) {
            case IMAGE -> "Photo";
            case VIDEO -> "Video";
            case FILE -> "Document";
            case VOICE -> "Voice message";
            default -> "";
        };
        String text = label.isEmpty() ? caption : (caption.isEmpty() ? label : label + ": " + caption);
        return TextUtil.truncate(text.isEmpty() ? "New message" : text, PREVIEW_LENGTH);
    }

    private static ApiException notFound() {
        return ApiException.notFound("NOTIFICATION_NOT_FOUND", "Notification not found");
    }
}
