package com.vtchat.security;

import org.springframework.messaging.MessagingException;

public class WebSocketAuthException extends MessagingException {

    public WebSocketAuthException(String message) {
        super(message);
    }
}
