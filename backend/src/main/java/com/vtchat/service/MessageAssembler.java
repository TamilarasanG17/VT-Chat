package com.vtchat.service;

import com.vtchat.dto.message.MessageResponse;
import com.vtchat.dto.message.MessageResponse.ReplyPreview;
import com.vtchat.dto.message.ReactionResponse;
import com.vtchat.entity.Message;
import com.vtchat.entity.MessageType;
import com.vtchat.repository.MediaDownloadRepository;
import com.vtchat.repository.MessageDeletionRepository;
import com.vtchat.repository.MessageReactionRepository;
import com.vtchat.util.TextUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class MessageAssembler {

    private static final int REPLY_PREVIEW_LENGTH = 150;

    private final MessageReactionRepository reactionRepository;
    private final MessageDeletionRepository deletionRepository;
    private final MediaDownloadRepository downloadRepository;

    public List<MessageResponse> assemble(Collection<Message> messages, Long viewerId) {
        if (messages.isEmpty()) {
            return List.of();
        }

        List<Long> liveIds = messages.stream().filter(m -> !m.isDeleted()).map(Message::getId).toList();
        Map<Long, List<ReactionResponse>> reactionsByMessage = liveIds.isEmpty() ? Map.of()
                : reactionRepository.findByMessageIds(liveIds).stream()
                .collect(Collectors.groupingBy(r -> r.getMessage().getId(), LinkedHashMap::new,
                        Collectors.mapping(ReactionResponse::from, Collectors.toList())));

        Set<Long> replyTargets = messages.stream()
                .filter(m -> !m.isDeleted() && m.getReplyToMessage() != null)
                .map(m -> m.getReplyToMessage().getId())
                .collect(Collectors.toSet());
        Set<Long> hiddenTargets = (viewerId == null || replyTargets.isEmpty()) ? Set.of()
                : new HashSet<>(deletionRepository.findHiddenIds(viewerId, replyTargets));

        List<Long> receivedFiles = viewerId == null ? List.of() : messages.stream()
                .filter(m -> !m.isDeleted() && m.getMessageType() == MessageType.FILE && !viewerId.equals(m.getSender().getId()))
                .map(Message::getId).toList();
        Set<Long> downloaded = receivedFiles.isEmpty() ? Set.of() : new HashSet<>(downloadRepository.findDownloadedIds(viewerId, receivedFiles));

        return messages.stream()
                .map(m -> toResponse(m, reactionsByMessage.getOrDefault(m.getId(), List.of()), hiddenTargets, viewerId, downloaded))
                .toList();
    }

    public MessageResponse assemble(Message message, Long viewerId) {
        return assemble(List.of(message), viewerId).get(0);
    }

    private static MessageResponse toResponse(Message m, List<ReactionResponse> reactions, Set<Long> hiddenTargets,
                                              Long viewerId, Set<Long> downloaded) {
        var updatedAt = m.getUpdatedAt() != null ? m.getUpdatedAt() : m.getCreatedAt();
        if (m.isDeleted()) {
            return new MessageResponse(m.getId(), m.getConversation().getId(), m.getSender().getId(), null,
                    false, true, m.getDeletedAt(), null, List.of(), false, null,
                    m.getMessageType(), null, null, null, null, null, null, null, null, null, m.getDeliveryStatus().name(), m.getCreatedAt(), updatedAt);
        }
        boolean media = m.getMessageType().isMedia();
        boolean call = m.getMessageType().isCall();
        ReplyPreview preview = null;
        Message target = m.getReplyToMessage();
        if (target != null) {
            boolean hidden = hiddenTargets.contains(target.getId());
            boolean hideText = target.isDeleted() || hidden;
            preview = new ReplyPreview(target.getId(), target.getSender().getId(),
                    hideText ? null : TextUtil.truncate(target.getContent(), REPLY_PREVIEW_LENGTH),
                    target.getMessageType(), hideText ? null : target.getMediaName(),
                    target.isDeleted(), hidden && !target.isDeleted());
        }
        boolean file = m.getMessageType() == MessageType.FILE;
        boolean received = file && viewerId != null && !viewerId.equals(m.getSender().getId());
        Boolean downloadable = file && viewerId != null ? received : null;
        Boolean downloadedByViewer = file && viewerId != null ? received && downloaded.contains(m.getId()) : null;
        return new MessageResponse(m.getId(), m.getConversation().getId(), m.getSender().getId(), m.getContent(),
                m.isEdited(), false, null, preview, reactions, m.isForwarded(), m.getForwardedFromMessageId(),
                m.getMessageType(), media ? "/api/media/" + m.getId() : null,
                media ? m.getMediaName() : null, media ? m.getMediaSize() : null,
                media ? m.getMimeType() : null, media || call ? m.getDuration() : null,
                call ? m.getCallId() : null, call ? m.getCallStatus() : null,
                downloadable, downloadedByViewer,
                m.getDeliveryStatus().name(), m.getCreatedAt(), updatedAt);
    }
}
