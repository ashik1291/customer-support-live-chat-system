package com.example.chat.websocket;

import com.example.chat.domain.ChatParticipant;
import java.io.Serializable;
import java.time.Instant;

public class SessionBinding implements Serializable {

    public enum Scope {
        CONVERSATION,
        QUEUE
    }

    private String sessionId;
    private Scope scope;
    private ChatParticipant participant;
    private String conversationId;
    private String queueAgentId;
    private Instant connectedAt;

    public SessionBinding() {}

    private SessionBinding(
            String sessionId,
            Scope scope,
            ChatParticipant participant,
            String conversationId,
            String queueAgentId,
            Instant connectedAt) {
        this.sessionId = sessionId;
        this.scope = scope;
        this.participant = participant;
        this.conversationId = conversationId;
        this.queueAgentId = queueAgentId;
        this.connectedAt = connectedAt;
    }

    public static SessionBinding conversation(String sessionId, ChatParticipant participant, String conversationId) {
        return new SessionBinding(sessionId, Scope.CONVERSATION, participant, conversationId, null, Instant.now());
    }

    public static SessionBinding queue(String sessionId, ChatParticipant participant) {
        String agentId = participant != null ? participant.getId() : null;
        return new SessionBinding(sessionId, Scope.QUEUE, participant, null, agentId, Instant.now());
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Scope getScope() {
        return scope;
    }

    public void setScope(Scope scope) {
        this.scope = scope;
    }

    public ChatParticipant getParticipant() {
        return participant;
    }

    public void setParticipant(ChatParticipant participant) {
        this.participant = participant;
    }

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getQueueAgentId() {
        return queueAgentId;
    }

    public void setQueueAgentId(String queueAgentId) {
        this.queueAgentId = queueAgentId;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public void setConnectedAt(Instant connectedAt) {
        this.connectedAt = connectedAt;
    }
}
