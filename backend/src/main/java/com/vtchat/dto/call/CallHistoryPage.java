package com.vtchat.dto.call;

import org.springframework.data.domain.Page;

import java.util.List;

public record CallHistoryPage(List<CallHistoryItem> items, int page, int size, long totalElements, int totalPages, boolean hasNext) {

    public static CallHistoryPage of(Page<CallHistoryItem> page) {
        return new CallHistoryPage(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements(),
                page.getTotalPages(), page.hasNext());
    }
}
