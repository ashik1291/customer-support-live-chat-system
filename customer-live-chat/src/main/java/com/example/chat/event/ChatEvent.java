package com.example.chat.event;

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
public class ChatEvent implements Serializable {

    private String eventId;
    private ChatEventType type;
    private String conversationId;
    private Instant occurredAt;
    private Map<String, Object> payload;
}

