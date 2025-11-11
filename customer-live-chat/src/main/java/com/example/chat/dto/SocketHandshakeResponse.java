package com.example.chat.dto;

import com.example.chat.domain.ChatParticipant;
import com.example.chat.domain.ConversationMetadata;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class SocketHandshakeResponse {
    ChatParticipant participant;
    ConversationMetadata conversation;
}

