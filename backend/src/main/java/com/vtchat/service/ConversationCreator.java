package com.vtchat.service;

import com.vtchat.entity.Conversation;
import com.vtchat.repository.ConversationRepository;
import com.vtchat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class ConversationCreator {

    public record Result(Long conversationId, boolean created) {
    }

    private final ConversationRepository conversations;
    private final UserRepository users;

    @Transactional
    public Result findOrCreate(Long lowerId, Long higherId) {
        Optional<Conversation> existing = conversations.findByUserOneIdAndUserTwoId(lowerId, higherId);
        if (existing.isPresent()) {
            return new Result(existing.get().getId(), false);
        }
        Conversation saved = conversations.saveAndFlush(Conversation.builder()
                .userOne(users.getReferenceById(lowerId))
                .userTwo(users.getReferenceById(higherId))
                .build());
        return new Result(saved.getId(), true);
    }

    @Transactional(readOnly = true)
    public Optional<Long> findId(Long lowerId, Long higherId) {
        return conversations.findByUserOneIdAndUserTwoId(lowerId, higherId).map(Conversation::getId);
    }
}
