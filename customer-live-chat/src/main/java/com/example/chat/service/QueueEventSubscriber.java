package com.example.chat.service;

import com.corundumstudio.socketio.SocketIOServer;
import com.example.chat.dto.QueueSnapshotPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.codec.TypedJsonJacksonCodec;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QueueEventSubscriber {

    public static final String QUEUE_ROOM = "agent-queue";
    public static final String QUEUE_EVENT = "queue:snapshot";

    private final RedissonClient redissonClient;
    private final RedisKeyFactory keyFactory;
    private final SocketIOServer socketIOServer;
    private final ObjectMapper objectMapper;

    private TypedJsonJacksonCodec snapshotCodec;
    private RTopic queueTopic;
    private int topicListenerId;

    @PostConstruct
    public void subscribe() {
        queueTopic = redissonClient.getTopic(keyFactory.queueTopicName(), snapshotCodec());
        topicListenerId = queueTopic.addListener(QueueSnapshotPayload.class, (channel, payload) -> broadcast(payload.getEntries()));
    }

    @PreDestroy
    public void shutdown() {
        if (queueTopic != null) {
            queueTopic.removeListener(topicListenerId);
        }
    }

    public void broadcast(List<?> entries) {
        if (entries == null) {
            return;
        }
        socketIOServer.getRoomOperations(QUEUE_ROOM).sendEvent(QUEUE_EVENT, entries);
    }

    private TypedJsonJacksonCodec snapshotCodec() {
        if (snapshotCodec == null) {
            snapshotCodec = new TypedJsonJacksonCodec(QueueSnapshotPayload.class, objectMapper);
        }
        return snapshotCodec;
    }
}
