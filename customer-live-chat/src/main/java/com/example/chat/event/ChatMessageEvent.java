package com.example.chat.event;

import com.example.chat.domain.ChatMessage;
import java.io.Serializable;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageEvent implements Serializable {

    private String eventId;
    private String conversationId;
    private ChatMessage message;
    private Instant occurredAt;
}

