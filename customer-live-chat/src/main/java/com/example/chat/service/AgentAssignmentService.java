package com.example.chat.service;

import com.example.chat.config.ChatProperties;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AgentAssignmentService {

    private final RedissonClient redissonClient;
    private final RedisKeyFactory keyFactory;
    private final ChatProperties chatProperties;

    public boolean canAssign(String agentId) {
        RSet<String> assignments = assignmentSet(agentId);
        return assignments.size() < chatProperties.getQueue().getMaxConcurrentByAgent();
    }

    public void registerAssignment(String agentId, String conversationId) {
        assignmentSet(agentId).add(conversationId);
    }

    public void removeAssignment(String agentId, String conversationId) {
        assignmentSet(agentId).remove(conversationId);
    }

    public Set<String> currentAssignments(String agentId) {
        return assignmentSet(agentId).readAll();
    }

    private RSet<String> assignmentSet(String agentId) {
        return redissonClient.getSet(keyFactory.agentConversationSetKey(agentId));
    }
}

