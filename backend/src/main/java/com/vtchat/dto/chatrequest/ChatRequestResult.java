package com.vtchat.dto.chatrequest;

import com.vtchat.dto.conversation.ConversationResponse;

public record ChatRequestResult(ChatRequestResponse request, ChatRequestStatusResponse relationship, ConversationResponse conversation) {
}
