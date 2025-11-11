package com.example.chat.domain;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMetadata implements Serializable {

    private String id;
    private ConversationStatus status;
    private ChatParticipant customer;
    private ChatParticipant agent;
    private Instant createdAt;
    private Instant updatedAt;
    private Instant acceptedAt;
    private Instant closedAt;
    private List<String> tags;
    private Map<String, Object> attributes;
}

