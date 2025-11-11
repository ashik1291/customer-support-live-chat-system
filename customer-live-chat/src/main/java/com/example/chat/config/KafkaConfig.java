package com.example.chat.config;

import com.example.chat.event.ChatEvent;
import com.example.chat.event.ChatMessageEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.kafka.KafkaProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@Configuration
public class KafkaConfig {

    @Bean
    public ProducerFactory<String, ChatEvent> chatEventProducerFactory(KafkaProperties properties) {
        return new DefaultKafkaProducerFactory<>(properties.buildProducerProperties());
    }

    @Bean
    public KafkaTemplate<String, ChatEvent> chatEventKafkaTemplate(
            ProducerFactory<String, ChatEvent> chatEventProducerFactory) {
        return new KafkaTemplate<>(chatEventProducerFactory);
    }

    @Bean
    public ProducerFactory<String, ChatMessageEvent> chatMessageProducerFactory(KafkaProperties properties) {
        return new DefaultKafkaProducerFactory<>(properties.buildProducerProperties());
    }

    @Bean
    public KafkaTemplate<String, ChatMessageEvent> chatMessageKafkaTemplate(
            ProducerFactory<String, ChatMessageEvent> chatMessageProducerFactory) {
        return new KafkaTemplate<>(chatMessageProducerFactory);
    }

    @Bean
    public NewTopic lifecycleTopic(ChatProperties chatProperties) {
        return TopicBuilder.name(chatProperties.getKafka().getLifecycleTopic())
                .partitions(6)
                .replicas(1)
                .compact()
                .build();
    }

    @Bean
    public NewTopic messageTopic(ChatProperties chatProperties) {
        return TopicBuilder.name(chatProperties.getKafka().getMessageTopic())
                .partitions(12)
                .replicas(1)
                .build();
    }
}

