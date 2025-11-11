package com.example.chat.service;

import com.example.chat.domain.ChatMessage;
import com.example.chat.domain.ConversationMetadata;
import java.util.List;
import java.util.Optional;

public interface ConversationRepository {

    void saveConversation(ConversationMetadata conversation);

    Optional<ConversationMetadata> getConversation(String conversationId);

    void deleteConversation(String conversationId);

    void appendMessage(ChatMessage message);

    List<ChatMessage> getMessages(String conversationId, int limit);

    List<ConversationMetadata> findAll();
}

