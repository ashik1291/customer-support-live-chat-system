package com.example.chat.service;

import com.example.chat.dto.QueueSnapshotPayload;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RTopic;
import org.redisson.api.RedissonClient;
import org.redisson.codec.TypedJsonJacksonCodec;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class QueueEventPublisher {

    private final RedissonClient redissonClient;
    private final RedisKeyFactory keyFactory;
    private final ObjectMapper objectMapper;

    private TypedJsonJacksonCodec snapshotCodec;

    public void publishSnapshot(QueueSnapshotPayload payload) {
        RTopic topic = redissonClient.getTopic(keyFactory.queueTopicName(), snapshotCodec());
        topic.publish(payload);
    }

    private TypedJsonJacksonCodec snapshotCodec() {
        if (snapshotCodec == null) {
            snapshotCodec = new TypedJsonJacksonCodec(QueueSnapshotPayload.class, objectMapper);
        }
        return snapshotCodec;
    }
}
