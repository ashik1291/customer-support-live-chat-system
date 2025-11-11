package com.example.chat.websocket;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.example.chat.domain.ChatMessage;
import com.example.chat.domain.ChatMessageType;
import com.example.chat.domain.ChatParticipant;
import com.example.chat.domain.ConversationMetadata;
import com.example.chat.domain.ConversationStatus;
import com.example.chat.dto.ChatMessagePayload;
import com.example.chat.dto.SocketHandshakeResponse;
import com.example.chat.service.ConversationService;
import com.example.chat.service.ParticipantIdentityService;
import com.example.chat.service.PresenceService;
import jakarta.annotation.PostConstruct;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class SocketIoChatGateway {

    private static final String MESSAGE_EVENT = "chat:message";
    private static final String SYSTEM_EVENT = "system:event";
    private static final String ERROR_EVENT = "system:error";

    private final SocketIOServer socketIOServer;
    private final ConversationService conversationService;
    private final PresenceService presenceService;
    private final ParticipantIdentityService participantIdentityService;

    private final Map<UUID, ChatParticipant> participantsByClient = new ConcurrentHashMap<>();
    private final Map<UUID, String> conversationByClient = new ConcurrentHashMap<>();

    @PostConstruct
    public void registerListeners() {
        socketIOServer.addConnectListener(this::handleConnect);
        socketIOServer.addDisconnectListener(this::handleDisconnect);
        socketIOServer.addEventListener(MESSAGE_EVENT, ChatMessagePayload.class, this::handleMessage);
    }

    private void handleConnect(SocketIOClient client) {
        try {
            String role = client.getHandshakeData().getSingleUrlParam("role");
            String token = client.getHandshakeData().getSingleUrlParam("token");
            String fingerprint = client.getHandshakeData().getSingleUrlParam("fingerprint");
            String conversationId = client.getHandshakeData().getSingleUrlParam("conversationId");
            String displayName = client.getHandshakeData().getSingleUrlParam("displayName");

            boolean isAgent = "agent".equalsIgnoreCase(role);
            ChatParticipant participant = isAgent
                    ? participantIdentityService.resolveAgent(token, displayName, Map.of())
                    : participantIdentityService.resolveCustomer(token, fingerprint, displayName, Map.of());

            ConversationMetadata conversation = resolveConversation(client, participant, conversationId, isAgent);

            participantsByClient.put(client.getSessionId(), participant);
            conversationByClient.put(client.getSessionId(), conversation.getId());
            presenceService.markPresent(participant.getId());
            client.joinRoom(conversation.getId());

            SocketHandshakeResponse response = SocketHandshakeResponse.builder()
                    .participant(participant)
                    .conversation(conversation)
                    .build();

            client.sendEvent(SYSTEM_EVENT, response);
            log.info("Client {} connected as {} for conversation {}", client.getSessionId(), role, conversation.getId());
        } catch (Exception e) {
            log.error("Failed to handle connect", e);
            participantsByClient.remove(client.getSessionId());
            conversationByClient.remove(client.getSessionId());
            client.sendEvent(ERROR_EVENT, Map.of("message", e.getMessage()));
            client.disconnect();
        }
    }

    private ConversationMetadata resolveConversation(
            SocketIOClient client, ChatParticipant participant, String conversationId, boolean isAgent) {
        if (StringUtils.hasText(conversationId)) {
            return conversationService
                    .getConversation(conversationId)
                    .map(conversation -> {
                        if (conversation.getStatus() == ConversationStatus.CLOSED) {
                            throw new IllegalStateException("Conversation is already closed");
                        }
                        return conversation;
                    })
                    .orElseThrow(() -> new IllegalArgumentException("Conversation not found"));
        }

        if (isAgent) {
            throw new IllegalArgumentException("Agents must join with a conversationId");
        }

        ConversationMetadata conversation = conversationService.startConversation(participant, Map.of());
        client.set("conversationId", conversation.getId());
        return conversation;
    }

    private void handleDisconnect(SocketIOClient client) {
        UUID sessionId = client.getSessionId();
        ChatParticipant participant = participantsByClient.remove(sessionId);
        conversationByClient.remove(sessionId);
        if (participant != null) {
            presenceService.markAbsent(participant.getId());
            log.info("Client {} disconnected", sessionId);
        }
    }

    private void handleMessage(SocketIOClient client, ChatMessagePayload payload, AckRequest ackSender) {
        ChatParticipant sender = participantsByClient.get(client.getSessionId());
        if (sender == null) {
            client.disconnect();
            return;
        }

        try {
            ChatMessageType messageType = ChatMessageType.valueOf(payload.getType().toUpperCase(Locale.ROOT));
            ChatMessage message =
                    conversationService.sendMessage(payload.getConversationId(), sender, payload.getContent(), messageType);

            socketIOServer.getRoomOperations(payload.getConversationId()).sendEvent(MESSAGE_EVENT, message);
            if (ackSender != null) {
                ackSender.sendAckData(message);
            }
        } catch (Exception ex) {
            log.error("Failed to send message", ex);
            if (ackSender != null) {
                ackSender.sendAckData(Map.of("error", ex.getMessage()));
            }
        }
    }
}

