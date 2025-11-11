package com.example.chat.service;

import com.example.chat.config.ChatProperties;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class PresenceService {

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory keyFactory;
    private final ChatProperties chatProperties;

    public PresenceService(
            StringRedisTemplate redisTemplate, RedisKeyFactory keyFactory, ChatProperties chatProperties) {
        this.redisTemplate = redisTemplate;
        this.keyFactory = keyFactory;
        this.chatProperties = chatProperties;
    }

    public void markPresent(String participantId) {
        redisTemplate.opsForValue()
                .set(keyFactory.presenceKey(participantId), Instant.now().toString(), chatProperties
                        .getRedis()
                        .getPresenceTtl());
    }

    public Optional<Instant> lastSeen(String participantId) {
        String result = redisTemplate.opsForValue().get(keyFactory.presenceKey(participantId));
        if (result == null) {
            return Optional.empty();
        }
        return Optional.of(Instant.parse(result));
    }

    public void markAbsent(String participantId) {
        redisTemplate.delete(keyFactory.presenceKey(participantId));
    }
}

