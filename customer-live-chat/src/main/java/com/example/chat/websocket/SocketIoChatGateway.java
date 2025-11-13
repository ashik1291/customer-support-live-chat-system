package com.example.chat.websocket;

import com.corundumstudio.socketio.AckRequest;
import com.corundumstudio.socketio.SocketIOClient;
import com.corundumstudio.socketio.SocketIOServer;
import com.example.chat.config.ChatProperties;
import com.example.chat.domain.ChatMessage;
import com.example.chat.domain.ChatMessageType;
import com.example.chat.domain.ChatParticipant;
import com.example.chat.domain.ConversationMetadata;
import com.example.chat.domain.ConversationStatus;
import com.example.chat.domain.QueueEntry;
import com.example.chat.dto.ChatMessagePayload;
import com.example.chat.dto.SocketHandshakeResponse;
import com.example.chat.event.ChatEvent;
import com.example.chat.event.ChatEventListener;
import com.example.chat.event.ChatEventType;
import com.example.chat.event.ChatMessageEvent;
import com.example.chat.service.AgentQueueService;
import com.example.chat.service.ConversationService;
import com.example.chat.service.ParticipantIdentityService;
import com.example.chat.service.PresenceService;
import com.example.chat.service.RedisKeyFactory;
import com.example.chat.websocket.SessionBinding;
import com.example.chat.websocket.SessionBinding.Scope;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RMapCache;
import org.redisson.api.RedissonClient;
import org.redisson.codec.TypedJsonJacksonCodec;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class SocketIoChatGateway implements ChatEventListener {

    private static final String MESSAGE_EVENT = "chat:message";
    private static final String SYSTEM_EVENT = "system:event";
    private static final String ERROR_EVENT = "system:error";
    private static final String QUEUE_ROOM = "agent-queue";
    private static final String QUEUE_EVENT = "queue:snapshot";

    private static final String PARAM_ROLE = "role";
    private static final String PARAM_TOKEN = "token";
    private static final String PARAM_FINGERPRINT = "fingerprint";
    private static final String PARAM_CONVERSATION_ID = "conversationId";
    private static final String PARAM_DISPLAY_NAME = "displayName";
    private static final String PARAM_SCOPE = "scope";
    private static final String SCOPE_QUEUE = "queue";

    private final SocketIOServer socketIOServer;
    private final PresenceService presenceService;
    private final ParticipantIdentityService participantIdentityService;
    private final ApplicationContext applicationContext;
    private final AgentQueueService agentQueueService;
    private final RedisKeyFactory keyFactory;
    private final RedissonClient redissonClient;
    private final ChatProperties chatProperties;
    private final ObjectMapper objectMapper;

    private TypedJsonJacksonCodec sessionCodec;

    private RMapCache<String, SessionBinding> sessionRegistry;

    @PostConstruct
    public void registerListeners() {
        sessionCodec = new TypedJsonJacksonCodec(String.class, SessionBinding.class, objectMapper);
        sessionRegistry = redissonClient.getMapCache(keyFactory.socketSessionMapKey(), sessionCodec);
        socketIOServer.addConnectListener(this::handleConnect);
        socketIOServer.addDisconnectListener(this::handleDisconnect);
        socketIOServer.addEventListener(MESSAGE_EVENT, ChatMessagePayload.class, this::handleMessage);
    }

    private void handleConnect(SocketIOClient client) {
        try {
            String role = client.getHandshakeData().getSingleUrlParam(PARAM_ROLE);
            String scope = client.getHandshakeData().getSingleUrlParam(PARAM_SCOPE);
            if (SCOPE_QUEUE.equalsIgnoreCase(scope)) {
                handleQueueConnect(client);
                return;
            }

            String token = client.getHandshakeData().getSingleUrlParam(PARAM_TOKEN);
            String fingerprint = client.getHandshakeData().getSingleUrlParam(PARAM_FINGERPRINT);
            String conversationId = client.getHandshakeData().getSingleUrlParam(PARAM_CONVERSATION_ID);
            String displayName = client.getHandshakeData().getSingleUrlParam(PARAM_DISPLAY_NAME);

            boolean isAgent = "agent".equalsIgnoreCase(role);
            ChatParticipant participant = isAgent
                    ? participantIdentityService.resolveAgent(token, displayName, Map.of())
                    : participantIdentityService.resolveCustomer(token, fingerprint, displayName, Map.of());

            ConversationMetadata conversation = resolveConversation(client, participant, conversationId, isAgent);

            client.set("participant", participant);
            client.set("conversationId", conversation.getId());

            SessionBinding binding = SessionBinding.conversation(client.getSessionId().toString(), participant, conversation.getId());
            storeSession(binding, chatProperties.getRedis().getConversationTtl());
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
            removeSession(client.getSessionId());
            client.sendEvent(ERROR_EVENT, Map.of("message", e.getMessage()));
            client.disconnect();
        }
    }

    private void handleQueueConnect(SocketIOClient client) {
        String token = client.getHandshakeData().getSingleUrlParam(PARAM_TOKEN);
        String displayName = client.getHandshakeData().getSingleUrlParam(PARAM_DISPLAY_NAME);
        ChatParticipant agent = participantIdentityService.resolveAgent(token, displayName, Map.of());
        UUID sessionId = client.getSessionId();
        client.set("participant", agent);
        client.set("scope", SessionBinding.Scope.QUEUE);
        SessionBinding binding = SessionBinding.queue(sessionId.toString(), agent);
        storeSession(binding, chatProperties.getRedis().getPresenceTtl());
        presenceService.markPresent(agent.getId());
        client.joinRoom(QUEUE_ROOM);
        List<QueueEntry> snapshot = agentQueueService.listQueue(0, chatProperties.getQueue().getBroadcastLimit());
        client.sendEvent(QUEUE_EVENT, snapshot);
        log.info("Agent {} subscribed to live queue updates", agent.getId());
    }

    private ConversationMetadata resolveConversation(
            SocketIOClient client, ChatParticipant participant, String conversationId, boolean isAgent) {
        if (StringUtils.hasText(conversationId)) {
            return applicationContext.getBean(ConversationService.class)
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

        ConversationMetadata conversation = applicationContext.getBean(ConversationService.class)
                .startConversation(participant, Map.of());
        client.set("conversationId", conversation.getId());
        return conversation;
    }

    private void handleDisconnect(SocketIOClient client) {
        UUID sessionId = client.getSessionId();
        SessionBinding binding = removeSession(sessionId);
        ChatParticipant participant = client.get("participant");
        if (binding != null) {
            participant = participant != null ? participant : binding.getParticipant();
            if (binding.getScope() == Scope.QUEUE && binding.getQueueAgentId() != null) {
                presenceService.markAbsent(binding.getQueueAgentId());
                log.info("Queue subscriber {} disconnected", sessionId);
                return;
            }
        }
        if (participant != null) {
            presenceService.markAbsent(participant.getId());
            log.info("Client {} disconnected", sessionId);
        }
    }

    private void handleMessage(SocketIOClient client, ChatMessagePayload payload, AckRequest ackSender) {
        ChatParticipant sender = client.get("participant");
        if (sender == null) {
            SessionBinding binding = sessionRegistry != null ? sessionRegistry.get(client.getSessionId().toString()) : null;
            sender = binding != null ? binding.getParticipant() : null;
        }
        if (sender == null) {
            client.disconnect();
            return;
        }

        try {
            ChatMessageType messageType = ChatMessageType.valueOf(payload.getType().toUpperCase(Locale.ROOT));
            ChatMessage message =
                    applicationContext.getBean(ConversationService.class)
                            .sendMessage(payload.getConversationId(), sender, payload.getContent(), messageType);

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

    @Override
    public void onLifecycleEvent(ChatEvent event) {
        if (event.getType() == ChatEventType.CONVERSATION_CLOSED) {
            log.debug("Conversation {} closed", event.getConversationId());
        }
    }

    @Override
    public void onMessageEvent(ChatMessageEvent event) {
        socketIOServer
                .getRoomOperations(event.getConversationId())
                .sendEvent(MESSAGE_EVENT, event.getMessage());
    }

    private void storeSession(SessionBinding binding, Duration ttl) {
        if (sessionRegistry == null || binding == null) {
            return;
        }
        if (ttl != null && !ttl.isNegative() && !ttl.isZero()) {
            sessionRegistry.fastPut(binding.getSessionId(), binding, ttl.toMillis(), TimeUnit.MILLISECONDS);
        } else {
            sessionRegistry.fastPut(binding.getSessionId(), binding);
        }
    }

    private SessionBinding removeSession(UUID sessionId) {
        if (sessionRegistry == null) {
            return null;
        }
        return sessionRegistry.remove(sessionId.toString());
    }

    @PreDestroy
    public void shutdown() {
        socketIOServer.removeAllListeners(MESSAGE_EVENT);
        socketIOServer.removeAllListeners(SYSTEM_EVENT);
        socketIOServer.removeAllListeners(ERROR_EVENT);
    }
}

