package com.vtchat.security;

public record AuthenticatedUser(Long id, String email, String username, String sessionId) {
}
