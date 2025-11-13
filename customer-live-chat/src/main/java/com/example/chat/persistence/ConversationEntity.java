package com.example.chat.persistence;

import com.example.chat.domain.ConversationStatus;
import com.example.chat.domain.ParticipantType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "chat_conversations")
public class ConversationEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false, length = 64)
    private String id;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ConversationStatus status;

    @Column(name = "customer_id", nullable = false, length = 128)
    private String customerId;

    @Column(name = "customer_display_name", length = 255)
    private String customerDisplayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", length = 32)
    private ParticipantType customerType;

    @Column(name = "customer_metadata", columnDefinition = "text")
    private String customerMetadata;

    @Column(name = "agent_id", length = 128)
    private String agentId;

    @Column(name = "agent_display_name", length = 255)
    private String agentDisplayName;

    @Enumerated(EnumType.STRING)
    @Column(name = "agent_type", length = 32)
    private ParticipantType agentType;

    @Column(name = "agent_metadata", columnDefinition = "text")
    private String agentMetadata;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "tags", columnDefinition = "text")
    private String tags;

    @Column(name = "attributes", columnDefinition = "text")
    private String attributes;

    @Version
    @Column(name = "version")
    private Long version;
}


