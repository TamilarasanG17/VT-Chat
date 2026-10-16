package com.vtchat.security;

import java.security.Principal;

public record StompUser(Long id, String username) implements Principal {

    @Override
    public String getName() {
        return String.valueOf(id);
    }

    public static StompUser from(Principal principal) {
        if (principal instanceof StompUser user) {
            return user;
        }
        throw new WebSocketAuthException("Authentication required");
    }
}
