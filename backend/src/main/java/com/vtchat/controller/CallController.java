package com.vtchat.controller;

import com.vtchat.dto.ApiResponse;
import com.vtchat.dto.call.CallHistoryItem;
import com.vtchat.dto.call.CallHistoryPage;
import com.vtchat.dto.call.IceServersResponse;
import com.vtchat.security.AuthenticatedUser;
import com.vtchat.service.CallSessionStore;
import com.vtchat.service.IceServerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CallController {

    private final CallSessionStore store;
    private final IceServerService iceServers;

    @GetMapping("/api/conversations/{conversationId}/calls")
    public ResponseEntity<ApiResponse<CallHistoryPage>> history(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                @PathVariable Long conversationId,
                                                                @RequestParam(required = false) Integer page,
                                                                @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(ApiResponse.ok("OK", store.history(principal.id(), conversationId, page, size)));
    }

    @GetMapping("/api/calls")
    public ResponseEntity<ApiResponse<CallHistoryPage>> myCalls(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                @RequestParam(required = false) Integer page,
                                                                @RequestParam(required = false) Integer size) {
        return ResponseEntity.ok(ApiResponse.ok("OK", store.historyOfUser(principal.id(), page, size)));
    }

    @GetMapping("/api/calls/ice-servers")
    public ResponseEntity<ApiResponse<IceServersResponse>> iceServers(@AuthenticationPrincipal AuthenticatedUser principal) {
        return ResponseEntity.ok(ApiResponse.ok("OK", iceServers.forUser(principal.id())));
    }

    @GetMapping("/api/calls/{callId}")
    public ResponseEntity<ApiResponse<CallHistoryItem>> get(@AuthenticationPrincipal AuthenticatedUser principal,
                                                         @PathVariable String callId) {
        return ResponseEntity.ok(ApiResponse.ok("OK", store.get(principal.id(), callId)));
    }
}
