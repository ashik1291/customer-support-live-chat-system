package com.example.chat.dto;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;

@Data
public class CreateConversationRequest {

    @NotBlank
    private String channel;

    private final Map<String, Object> attributes = new HashMap<>();

    @JsonAnySetter
    public void addAttribute(String key, Object value) {
        attributes.put(key, value);
    }
}

