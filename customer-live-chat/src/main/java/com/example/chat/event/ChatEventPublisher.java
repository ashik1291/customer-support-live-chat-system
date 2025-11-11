package com.example.chat.event;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class ChatEventPublisher {

    private final List<ChatEventListener> listeners;
    private final KafkaTemplate<String, ChatEvent> chatEventKafkaTemplate;
    private final KafkaTemplate<String, ChatMessageEvent> chatMessageKafkaTemplate;
    private final com.example.chat.config.ChatProperties chatProperties;

    public void publishLifecycleEvent(ChatEvent event) {
        listeners.forEach(listener -> listener.onLifecycleEvent(event));
        chatEventKafkaTemplate.send(chatProperties.getKafka().getLifecycleTopic(), event.getConversationId(), event);
    }

    public void publishMessageEvent(ChatMessageEvent event) {
        listeners.forEach(listener -> listener.onMessageEvent(event));
        chatMessageKafkaTemplate.send(chatProperties.getKafka().getMessageTopic(), event.getConversationId(), event);
    }
}

