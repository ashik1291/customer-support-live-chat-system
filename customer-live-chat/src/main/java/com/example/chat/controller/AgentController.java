package com.example.chat.controller;

import com.example.chat.domain.ChatParticipant;
import com.example.chat.domain.ConversationMetadata;
import com.example.chat.domain.QueueEntry;
import com.example.chat.domain.ConversationStatus;
import com.example.chat.dto.AgentAcceptRequest;
import com.example.chat.dto.AgentActionRequest;
import com.example.chat.service.AgentQueueService;
import com.example.chat.service.ConversationService;
import com.example.chat.service.ParticipantIdentityService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/agent")
public class AgentController {

    private final AgentQueueService agentQueueService;
    private final ConversationService conversationService;
    private final ParticipantIdentityService participantIdentityService;

    public AgentController(
            AgentQueueService agentQueueService,
            ConversationService conversationService,
            ParticipantIdentityService participantIdentityService) {
        this.agentQueueService = agentQueueService;
        this.conversationService = conversationService;
        this.participantIdentityService = participantIdentityService;
    }

    @GetMapping("/queue")
    public ResponseEntity<List<QueueEntry>> listQueue() {
        return ResponseEntity.ok(agentQueueService.listQueue(100));
    }

    @GetMapping("/conversations")
    public ResponseEntity<List<ConversationMetadata>> listAgentConversations(
            @RequestHeader("X-Agent-Id") String agentId,
            @RequestParam(name = "status", required = false) List<String> statuses) {
        Set<ConversationStatus> statusFilters = parseStatuses(statuses);
        List<ConversationMetadata> conversations = conversationService.getConversationsForAgent(agentId, statusFilters);
        return ResponseEntity.ok(conversations);
    }

    @PostMapping("/conversations/{conversationId}/accept")
    public ResponseEntity<ConversationMetadata> acceptConversation(
            @PathVariable String conversationId, @Valid @RequestBody AgentAcceptRequest request) {
        ChatParticipant agent = participantIdentityService.resolveAgent(
                request.getAgentId(), request.getDisplayName(), request.getMetadata());
        ConversationMetadata conversation = conversationService.acceptConversation(agent, conversationId);
        return ResponseEntity.ok(conversation);
    }

    @GetMapping("/conversations/{conversationId}/messages")
    public ResponseEntity<List<com.example.chat.domain.ChatMessage>> listConversationMessages(
            @PathVariable String conversationId,
            @RequestHeader("X-Agent-Id") String agentId,
            @RequestParam(name = "limit", defaultValue = "100") int limit) {
        ConversationMetadata conversation = conversationService
                .getConversation(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));

        if (conversation.getAgent() == null || !agentId.equals(conversation.getAgent().getId())) {
            throw new IllegalArgumentException("Agent not assigned to this conversation");
        }

        int resolvedLimit = Math.max(1, Math.min(limit, 500));
        return ResponseEntity.ok(conversationService.getRecentMessages(conversationId, resolvedLimit));
    }

    @PostMapping("/conversations/{conversationId}/close")
    public ResponseEntity<ConversationMetadata> closeConversation(
            @PathVariable String conversationId, @RequestBody(required = false) AgentActionRequest request) {
        ChatParticipant agent = null;
        if (request != null && StringUtils.hasText(request.getAgentId())) {
            agent = participantIdentityService.resolveAgent(
                    request.getAgentId(), request.getDisplayName(), request.getMetadata());
        }
        ConversationMetadata conversation = conversationService.closeConversation(conversationId, agent);
        return ResponseEntity.ok(conversation);
    }

    private Set<ConversationStatus> parseStatuses(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return Set.of();
        }

        return statuses.stream()
                .filter(StringUtils::hasText)
                .map(status -> {
                    try {
                        return ConversationStatus.valueOf(status.toUpperCase(Locale.ROOT));
                    } catch (IllegalArgumentException ex) {
                        throw new IllegalArgumentException("Unsupported status filter: " + status, ex);
                    }
                })
                .collect(Collectors.toSet());
    }
}

