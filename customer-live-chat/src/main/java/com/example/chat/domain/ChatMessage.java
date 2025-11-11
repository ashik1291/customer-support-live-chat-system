package com.example.chat.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage implements Serializable {

    private String id;
    private String conversationId;
    private ChatMessageType type;
    private ChatParticipant sender;
    private String content;
    private Map<String, Object> metadata;
    private Instant timestamp;
}

