package com.vtchat.controller;

import com.vtchat.dto.presence.TypingRequest;
import com.vtchat.security.StompUser;
import com.vtchat.service.TypingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Slf4j
@Controller
@RequiredArgsConstructor
public class PresenceWebSocketController {

    private final TypingService typing;

    @MessageMapping("/chat.typing")
    public void typing(@Payload TypingRequest request, Principal principal) {
        typing.typing(StompUser.from(principal).id(), request);
    }

    @MessageExceptionHandler(Exception.class)
    public void ignore(Exception ex) {
        log.debug("Typing signal ignored: {}", ex.getClass().getSimpleName());
    }
}
