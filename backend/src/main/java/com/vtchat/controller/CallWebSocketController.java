package com.vtchat.controller;

import com.vtchat.dto.call.CallAction;
import com.vtchat.dto.call.CallAnswer;
import com.vtchat.dto.call.CallOffer;
import com.vtchat.dto.call.IceCandidate;
import com.vtchat.dto.message.WsErrorResponse;
import com.vtchat.exception.ApiException;
import com.vtchat.security.StompUser;
import com.vtchat.security.WebSocketAuthException;
import com.vtchat.service.CallService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Slf4j
@Controller
@RequiredArgsConstructor
public class CallWebSocketController {

    private final CallService calls;

    @MessageMapping("/call.offer")
    public void offer(@Payload CallOffer offer, Principal principal) {
        calls.offer(StompUser.from(principal).id(), offer);
    }

    @MessageMapping("/call.answer")
    public void answer(@Payload CallAnswer answer, Principal principal) {
        calls.answer(StompUser.from(principal).id(), answer);
    }

    @MessageMapping("/call.ice")
    public void ice(@Payload IceCandidate ice, Principal principal) {
        calls.ice(StompUser.from(principal).id(), ice);
    }

    @MessageMapping("/call.reject")
    public void reject(@Payload CallAction action, Principal principal) {
        calls.reject(StompUser.from(principal).id(), action);
    }

    @MessageMapping("/call.cancel")
    public void cancel(@Payload CallAction action, Principal principal) {
        calls.cancel(StompUser.from(principal).id(), action);
    }

    @MessageMapping("/call.end")
    public void end(@Payload CallAction action, Principal principal) {
        calls.end(StompUser.from(principal).id(), action);
    }

    @MessageMapping("/call.connected")
    public void connected(@Payload CallAction action, Principal principal) {
        calls.connected(StompUser.from(principal).id(), action);
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
        log.error("Unexpected error while handling a call signal", ex);
        return new WsErrorResponse(500, "INTERNAL_ERROR", "Something went wrong. Please try again.");
    }
}
