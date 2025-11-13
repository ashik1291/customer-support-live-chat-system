package com.example.chat.service;

import com.example.chat.config.ChatProperties;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PresenceService {

    private final RedissonClient redissonClient;
    private final RedisKeyFactory keyFactory;
    private final ChatProperties chatProperties;

    public void markPresent(String participantId) {
        RBucket<String> bucket = bucket(participantId);
        bucket.set(Instant.now().toString());
        bucket.expire(chatProperties.getRedis().getPresenceTtl());
    }

    public Optional<Instant> lastSeen(String participantId) {
        String result = bucket(participantId).get();
        if (result == null) {
            return Optional.empty();
        }
        return Optional.of(Instant.parse(result));
    }

    public void markAbsent(String participantId) {
        bucket(participantId).delete();
    }

    private RBucket<String> bucket(String participantId) {
        return redissonClient.getBucket(keyFactory.presenceKey(participantId));
    }
}

