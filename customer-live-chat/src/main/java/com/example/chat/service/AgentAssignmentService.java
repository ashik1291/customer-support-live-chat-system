package com.example.chat.service;

import com.example.chat.config.ChatProperties;
import java.util.Set;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class AgentAssignmentService {

    private final StringRedisTemplate redisTemplate;
    private final RedisKeyFactory keyFactory;
    private final ChatProperties chatProperties;

    public AgentAssignmentService(
            StringRedisTemplate redisTemplate, RedisKeyFactory keyFactory, ChatProperties chatProperties) {
        this.redisTemplate = redisTemplate;
        this.keyFactory = keyFactory;
        this.chatProperties = chatProperties;
    }

    public boolean canAssign(String agentId) {
        String key = keyFactory.agentConversationSetKey(agentId);
        Long size = redisTemplate.opsForSet().size(key);
        if (size == null) {
            return true;
        }
        return size < chatProperties.getQueue().getMaxConcurrentByAgent();
    }

    public void registerAssignment(String agentId, String conversationId) {
        String key = keyFactory.agentConversationSetKey(agentId);
        redisTemplate.opsForSet().add(key, conversationId);
    }

    public void removeAssignment(String agentId, String conversationId) {
        String key = keyFactory.agentConversationSetKey(agentId);
        redisTemplate.opsForSet().remove(key, conversationId);
    }

    public Set<String> currentAssignments(String agentId) {
        String key = keyFactory.agentConversationSetKey(agentId);
        return redisTemplate.opsForSet().members(key);
    }
}

