package com.vtchat.controller;

import com.vtchat.dto.ApiResponse;
import com.vtchat.dto.user.UserSearchResponse;
import com.vtchat.security.AuthenticatedUser;
import com.vtchat.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users/search")
@RequiredArgsConstructor
public class UserSearchController {

    private final UserService userService;

    @GetMapping
    public ResponseEntity<ApiResponse<List<UserSearchResponse>>> search(@AuthenticationPrincipal AuthenticatedUser principal,
                                                                        @RequestParam("query") String query) {
        return ResponseEntity.ok(ApiResponse.ok("OK", userService.searchUsers(principal.id(), query)));
    }
}
