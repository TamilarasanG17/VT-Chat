package com.vtchat.service;

import com.vtchat.entity.ChatRequest;
import com.vtchat.entity.ChatRequestStatus;
import com.vtchat.repository.ChatRequestRepository;
import com.vtchat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ChatRequestWriter {

    private final ChatRequestRepository requests;
    private final UserRepository users;

    @Transactional
    public ChatRequest insertPending(Long senderId, Long receiverId) {
        ChatRequest saved = requests.saveAndFlush(ChatRequest.builder()
                .sender(users.getReferenceById(senderId))
                .receiver(users.getReferenceById(receiverId))
                .status(ChatRequestStatus.PENDING)
                .pairLowId(Math.min(senderId, receiverId))
                .pairHighId(Math.max(senderId, receiverId))
                .pendingPairKey(ChatRequest.pairKey(senderId, receiverId))
                .build());
        return requests.findWithUsersById(saved.getId()).orElse(saved);
    }
}
