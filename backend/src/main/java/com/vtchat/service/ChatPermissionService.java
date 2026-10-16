package com.vtchat.service;

import com.vtchat.entity.ChatRequest;
import com.vtchat.entity.ChatRequestStatus;
import com.vtchat.entity.Conversation;
import com.vtchat.exception.ApiException;
import com.vtchat.repository.ChatRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ChatPermissionService {

    private final ChatRequestRepository requests;

    @Transactional(readOnly = true)
    public boolean isMessagingAllowed(Long userOneId, Long userTwoId) {
        Long low = Math.min(userOneId, userTwoId);
        Long high = Math.max(userOneId, userTwoId);
        if (requests.existsByPairLowIdAndPairHighIdAndStatus(low, high, ChatRequestStatus.ACCEPTED)) {
            return true;
        }
        return !requests.existsByPairLowIdAndPairHighId(low, high);
    }

    @Transactional(readOnly = true)
    public java.util.Set<Long> blockedPeerIds(Long userId) {
        java.util.Set<Long> blocked = new java.util.HashSet<>();
        for (Object[] pair : requests.findBlockedPairs(userId)) {
            Long low = (Long) pair[0];
            Long high = (Long) pair[1];
            blocked.add(userId.equals(low) ? high : low);
        }
        return blocked;
    }

    public void requireMessagingAllowed(Conversation conversation) {
        if (!isMessagingAllowed(conversation.getUserOne().getId(), conversation.getUserTwo().getId())) {
            throw ApiException.forbidden("MESSAGING_NOT_ENABLED", "Messaging is not enabled for this conversation");
        }
    }

    public void requireMessagingAllowed(Long userOneId, Long userTwoId) {
        if (!isMessagingAllowed(userOneId, userTwoId)) {
            throw ApiException.forbidden("MESSAGING_NOT_ENABLED", "Messaging is not enabled for this conversation");
        }
    }

    static boolean accepted(ChatRequest r) {
        return r != null && r.getStatus() == ChatRequestStatus.ACCEPTED;
    }
}
