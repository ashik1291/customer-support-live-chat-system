package com.example.chat.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;

@Data
public class AgentActionRequest {

    @JsonAlias("agentToken")
    private String agentId;

    private String displayName;

    private final Map<String, Object> metadata = new HashMap<>();
}

