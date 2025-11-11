package com.example.chat.dto;

import com.example.chat.domain.ParticipantType;
import com.fasterxml.jackson.annotation.JsonAlias;
import jakarta.validation.constraints.NotBlank;
import java.util.HashMap;
import java.util.Map;
import lombok.Data;

@Data
public class SendMessageRequest {

    @NotBlank
    @JsonAlias("senderToken")
    private String senderId;

    private String senderDisplayName;

    private String senderType = ParticipantType.CUSTOMER.name();

    private final Map<String, Object> metadata = new HashMap<>();

    @NotBlank
    private String content;

    private String type = "TEXT";
}

