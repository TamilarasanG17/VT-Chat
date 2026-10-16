package com.vtchat.service;

import com.vtchat.entity.ChatRequestStatus;
import com.vtchat.exception.ApiException;
import com.vtchat.repository.ChatRequestRepository;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatPermissionServiceTest {

    private final ChatRequestRepository requests = mock(ChatRequestRepository.class);
    private final ChatPermissionService permissions = new ChatPermissionService(requests);

    @Test
    void legacyConversationWithoutAnyRequestMayMessage() {
        when(requests.existsByPairLowIdAndPairHighIdAndStatus(1L, 2L, ChatRequestStatus.ACCEPTED)).thenReturn(false);
        when(requests.existsByPairLowIdAndPairHighId(1L, 2L)).thenReturn(false);
        assertTrue(permissions.isMessagingAllowed(2L, 1L));
    }

    @Test
    void acceptedPairMayMessage() {
        when(requests.existsByPairLowIdAndPairHighIdAndStatus(1L, 2L, ChatRequestStatus.ACCEPTED)).thenReturn(true);
        assertTrue(permissions.isMessagingAllowed(1L, 2L));
    }

    @Test
    void pairWithOnlyPendingOrDeclinedRequestsMayNotMessage() {
        when(requests.existsByPairLowIdAndPairHighIdAndStatus(1L, 2L, ChatRequestStatus.ACCEPTED)).thenReturn(false);
        when(requests.existsByPairLowIdAndPairHighId(1L, 2L)).thenReturn(true);
        assertFalse(permissions.isMessagingAllowed(1L, 2L));
        ApiException e = assertThrows(ApiException.class, () -> permissions.requireMessagingAllowed(1L, 2L));
        assertTrue(e.getMessage().toLowerCase().contains("messaging"));
    }

    @Test
    void endedConnectionMayNotMessageUntilANewAcceptedRequestExists() {
        when(requests.existsByPairLowIdAndPairHighIdAndStatus(1L, 2L, ChatRequestStatus.ACCEPTED)).thenReturn(false);
        when(requests.existsByPairLowIdAndPairHighId(1L, 2L)).thenReturn(true);
        assertFalse(permissions.isMessagingAllowed(2L, 1L));
        when(requests.existsByPairLowIdAndPairHighIdAndStatus(1L, 2L, ChatRequestStatus.ACCEPTED)).thenReturn(true);
        assertTrue(permissions.isMessagingAllowed(2L, 1L));
    }

    @Test
    void blockedPeerIdsReturnsTheOtherSideOfEachBlockedPair() {
        when(requests.findBlockedPairs(5L)).thenReturn(java.util.List.of(new Object[]{2L, 5L}, new Object[]{5L, 9L}));
        assertTrue(permissions.blockedPeerIds(5L).containsAll(java.util.Set.of(2L, 9L)));
    }
}
