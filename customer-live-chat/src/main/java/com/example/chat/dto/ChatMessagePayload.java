package com.example.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ChatMessagePayload {

    @NotBlank
    private String conversationId;

    @NotBlank
    private String content;

    private String type = "TEXT";
}

