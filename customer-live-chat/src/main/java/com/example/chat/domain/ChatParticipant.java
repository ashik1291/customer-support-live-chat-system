package com.example.chat.domain;

import java.io.Serializable;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatParticipant implements Serializable {

    private String id;
    private ParticipantType type;
    private String displayName;
    private Map<String, Object> metadata;
}

