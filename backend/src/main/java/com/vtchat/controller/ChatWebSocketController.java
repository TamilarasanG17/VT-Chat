package com.vtchat.controller;

import com.vtchat.dto.conversation.ActiveChatRequest;
import com.vtchat.dto.message.DeleteMessageRequest;
import com.vtchat.dto.message.EditMessageRequest;
import com.vtchat.dto.message.ReactionRequest;
import com.vtchat.dto.message.ReadRequest;
import com.vtchat.dto.message.SendMessageRequest;
import com.vtchat.dto.message.WsErrorResponse;
import com.vtchat.exception.ApiException;
import com.vtchat.security.StompUser;
import com.vtchat.security.WebSocketAuthException;
import com.vtchat.service.MessageActionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final MessageActionService actions;
    private final com.vtchat.service.MessageService messageService;
    private final com.vtchat.service.ActiveChatRegistry activeChats;

    @MessageMapping("/chat.send")
    public void send(@Payload SendMessageRequest request, Principal principal) {
        actions.send(StompUser.from(principal).id(), request);
    }

    @MessageMapping("/chat.read")
    public void read(@Payload ReadRequest request, Principal principal) {
        actions.markRead(StompUser.from(principal).id(), request.conversationId(), request.lastReadMessageId());
    }

    @MessageMapping("/chat.active")
    public void active(@Payload ActiveChatRequest request, Principal principal,
                       @Header("simpSessionId") String sessionId) {
        Long userId = StompUser.from(principal).id();
        Long conversationId = request == null ? null : request.conversationId();
        if (conversationId != null) {
            messageService.assertParticipant(conversationId, userId);
        }
        activeChats.set(sessionId, userId, conversationId);
    }

    @MessageMapping("/message.react")
    public void react(@Payload ReactionRequest request, Principal principal) {
        actions.react(StompUser.from(principal).id(), request.messageId(), request.reaction());
    }

    @MessageMapping("/message.unreact")
    public void unreact(@Payload ReactionRequest request, Principal principal) {
        actions.unreact(StompUser.from(principal).id(), request.messageId());
    }

    @MessageMapping("/message.edit")
    public void edit(@Payload EditMessageRequest request, Principal principal) {
        actions.edit(StompUser.from(principal).id(), request.messageId(), request.content());
    }

    @MessageMapping("/message.delete")
    public void delete(@Payload DeleteMessageRequest request, Principal principal) {
        actions.delete(StompUser.from(principal).id(), request.messageId(), request.scope());
    }

    @MessageExceptionHandler(ApiException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public WsErrorResponse handleApiException(ApiException ex) {
        return new WsErrorResponse(ex.getStatus().value(), ex.getCode(), ex.getMessage());
    }

    @MessageExceptionHandler(WebSocketAuthException.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public WsErrorResponse handleAuth(WebSocketAuthException ex) {
        return new WsErrorResponse(401, "UNAUTHORIZED", "Please log in again");
    }

    @MessageExceptionHandler(Exception.class)
    @SendToUser(destinations = "/queue/errors", broadcast = false)
    public WsErrorResponse handleUnexpected(Exception ex) {
        log.error("Unexpected error while handling a chat message", ex);
        return new WsErrorResponse(500, "INTERNAL_ERROR", "Something went wrong. Please try again.");
    }
}
