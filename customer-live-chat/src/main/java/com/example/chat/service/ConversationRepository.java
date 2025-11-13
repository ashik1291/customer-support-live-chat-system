package com.example.chat.service;

import com.example.chat.domain.ChatMessage;
import com.example.chat.domain.ConversationMetadata;
import com.example.chat.domain.ConversationStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ConversationRepository {

    void saveConversation(ConversationMetadata conversation);

    Optional<ConversationMetadata> getConversation(String conversationId);

    void deleteConversation(String conversationId);

    void appendMessage(ChatMessage message);

    List<ChatMessage> getMessages(String conversationId, int limit);

    List<ConversationMetadata> findAll();

    List<ConversationMetadata> findForAgent(String agentId, Set<ConversationStatus> statuses);

    List<ConversationMetadata> findStaleConversations(Instant inactivityCutoff, Instant maxDurationCutoff);
}

