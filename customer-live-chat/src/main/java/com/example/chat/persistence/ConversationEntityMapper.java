package com.example.chat.persistence;

import com.example.chat.domain.ChatParticipant;
import com.example.chat.domain.ConversationMetadata;
import com.example.chat.domain.ConversationStatus;
import com.example.chat.domain.ParticipantType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class ConversationEntityMapper {

    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {};
    private static final TypeReference<List<String>> LIST_TYPE = new TypeReference<>() {};

    private final ObjectMapper objectMapper;

    public ConversationEntity toEntity(ConversationMetadata metadata) {
        ConversationEntity entity = new ConversationEntity();
        entity.setId(metadata.getId());
        entity.setStatus(metadata.getStatus());

        if (metadata.getCustomer() != null) {
            entity.setCustomerId(metadata.getCustomer().getId());
            entity.setCustomerDisplayName(metadata.getCustomer().getDisplayName());
            entity.setCustomerType(metadata.getCustomer().getType());
            entity.setCustomerMetadata(writeJson(metadata.getCustomer().getMetadata()));
        }

        if (metadata.getAgent() != null) {
            entity.setAgentId(metadata.getAgent().getId());
            entity.setAgentDisplayName(metadata.getAgent().getDisplayName());
            entity.setAgentType(metadata.getAgent().getType());
            entity.setAgentMetadata(writeJson(metadata.getAgent().getMetadata()));
        } else {
            entity.setAgentId(null);
            entity.setAgentDisplayName(null);
            entity.setAgentType(null);
            entity.setAgentMetadata(null);
        }

        entity.setCreatedAt(metadata.getCreatedAt());
        entity.setUpdatedAt(metadata.getUpdatedAt());
        entity.setAcceptedAt(metadata.getAcceptedAt());
        entity.setClosedAt(metadata.getClosedAt());
        entity.setTags(writeJson(metadata.getTags()));
        entity.setAttributes(writeJson(metadata.getAttributes()));
        entity.setVersion(metadata.getVersion());
        return entity;
    }

    public ConversationMetadata toMetadata(ConversationEntity entity) {
        if (entity == null) {
            return null;
        }

        ConversationMetadata metadata = new ConversationMetadata();
        metadata.setId(entity.getId());
        metadata.setStatus(defaultStatus(entity.getStatus()));
        metadata.setCustomer(buildParticipant(
                entity.getCustomerId(),
                entity.getCustomerDisplayName(),
                entity.getCustomerType(),
                readMap(entity.getCustomerMetadata())));
        metadata.setAgent(buildParticipant(
                entity.getAgentId(),
                entity.getAgentDisplayName(),
                entity.getAgentType(),
                readMap(entity.getAgentMetadata())));
        metadata.setCreatedAt(defaultInstant(entity.getCreatedAt()));
        metadata.setUpdatedAt(entity.getUpdatedAt());
        metadata.setAcceptedAt(entity.getAcceptedAt());
        metadata.setClosedAt(entity.getClosedAt());
        metadata.setTags(readList(entity.getTags()));
        metadata.setAttributes(readMap(entity.getAttributes()));
        metadata.setVersion(entity.getVersion());
        return metadata;
    }

    private ConversationStatus defaultStatus(ConversationStatus status) {
        return status != null ? status : ConversationStatus.OPEN;
    }

    private Instant defaultInstant(Instant instant) {
        return instant != null ? instant : Instant.now();
    }

    private ChatParticipant buildParticipant(
            String id, String displayName, ParticipantType type, Map<String, Object> metadata) {
        if (!StringUtils.hasText(id)) {
            return null;
        }
        return ChatParticipant.builder()
                .id(id)
                .displayName(displayName)
                .type(type)
                .metadata(metadata != null ? metadata : Collections.emptyMap())
                .build();
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Map<?, ?> map && map.isEmpty()) {
            return null;
        }
        if (value instanceof List<?> list && list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Unable to serialize value", e);
        }
    }

    private Map<String, Object> readMap(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyMap();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }

    private List<String> readList(String json) {
        if (!StringUtils.hasText(json)) {
            return Collections.emptyList();
        }
        try {
            List<String> result = objectMapper.readValue(json, LIST_TYPE);
            return CollectionUtils.isEmpty(result) ? Collections.emptyList() : result;
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }
}


