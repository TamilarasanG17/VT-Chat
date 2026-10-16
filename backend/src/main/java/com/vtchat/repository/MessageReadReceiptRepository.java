package com.vtchat.repository;

import com.vtchat.entity.MessageReadReceipt;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MessageReadReceiptRepository extends JpaRepository<MessageReadReceipt, Long> {

    boolean existsByMessageIdAndUserId(Long messageId, Long userId);
}
