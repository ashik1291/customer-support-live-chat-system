package com.example.chat.persistence;

import com.example.chat.config.ChatProperties;
import com.example.chat.domain.ChatMessage;
import com.example.chat.domain.ConversationMetadata;
import com.example.chat.domain.ConversationStatus;
import com.example.chat.service.ConversationRepository;
import com.example.chat.service.RedisKeyFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RList;
import org.redisson.api.RedissonClient;
import org.redisson.codec.TypedJsonJacksonCodec;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Repository
@Primary
@RequiredArgsConstructor
public class JpaConversationRepository implements ConversationRepository {

    private final ConversationJpaRepository conversationJpaRepository;
    private final ConversationEntityMapper mapper;
    private final RedissonClient redissonClient;
    private final RedisKeyFactory keyFactory;
    private final ChatProperties chatProperties;
    private final ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    private TypedJsonJacksonCodec messageCodec;

    @Override
    @Transactional
    public void saveConversation(ConversationMetadata conversation) {
        ConversationMetadata normalized = ensureTimestamps(conversation);
        ConversationEntity entity = mapper.toEntity(normalized);
        conversationJpaRepository.save(entity);
        normalized.setVersion(entity.getVersion());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ConversationMetadata> getConversation(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return Optional.empty();
        }
        return conversationJpaRepository.findById(conversationId).map(mapper::toMetadata);
    }

    @Override
    @Transactional
    public void deleteConversation(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return;
        }
        conversationJpaRepository.deleteById(conversationId);
        messageList(conversationId).delete();
    }

    @Override
    public void appendMessage(ChatMessage message) {
        if (message == null || !StringUtils.hasText(message.getConversationId())) {
            return;
        }
        RList<ChatMessage> list = messageList(message.getConversationId());
        list.add(message);
        expireMessages(list);
    }

    @Override
    public List<ChatMessage> getMessages(String conversationId, int limit) {
        if (!StringUtils.hasText(conversationId) || limit <= 0) {
            return Collections.emptyList();
        }
        RList<ChatMessage> list = messageList(conversationId);
        int size = list.size();
        if (size == 0) {
            return Collections.emptyList();
        }
        int from = Math.max(0, size - limit);
        return list.range(from, size - 1);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationMetadata> findAll() {
        return conversationJpaRepository.findAll().stream()
                .map(mapper::toMetadata)
                .collect(Collectors.toUnmodifiableList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationMetadata> findForAgent(String agentId, Set<ConversationStatus> statuses) {
        if (!StringUtils.hasText(agentId)) {
            return Collections.emptyList();
        }
        List<ConversationEntity> entities;
        if (CollectionUtils.isEmpty(statuses)) {
            entities = conversationJpaRepository.findByAgentId(agentId);
        } else {
            entities = conversationJpaRepository.findForAgent(agentId, statuses.stream().toList());
        }
        return entities.stream().map(mapper::toMetadata).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<ConversationMetadata> findStaleConversations(Instant inactivityCutoff, Instant maxDurationCutoff) {
        if (inactivityCutoff == null && maxDurationCutoff == null) {
            return Collections.emptyList();
        }

        var cb = entityManager.getCriteriaBuilder();
        var cq = cb.createQuery(ConversationEntity.class);
        var root = cq.from(ConversationEntity.class);

        var predicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
        predicates.add(cb.notEqual(root.get("status"), ConversationStatus.CLOSED));

        var triggerPredicates = new ArrayList<jakarta.persistence.criteria.Predicate>();
        if (inactivityCutoff != null) {
            triggerPredicates.add(cb.lessThan(cb.<Instant>coalesce(root.get("updatedAt"), root.get("createdAt")), inactivityCutoff));
        }
        if (maxDurationCutoff != null) {
            triggerPredicates.add(cb.lessThan(root.get("createdAt"), maxDurationCutoff));
        }

        if (triggerPredicates.isEmpty()) {
            return Collections.emptyList();
        }

        jakarta.persistence.criteria.Predicate trigger;
        if (triggerPredicates.size() == 1) {
            trigger = triggerPredicates.get(0);
        } else {
            trigger = cb.or(triggerPredicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        }
        predicates.add(trigger);

        cq.where(cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0])));

        TypedQuery<ConversationEntity> query = entityManager.createQuery(cq);
        return query.getResultList().stream().map(mapper::toMetadata).toList();
    }

    private ConversationMetadata ensureTimestamps(ConversationMetadata conversation) {
        Instant now = Instant.now();
        if (conversation.getCreatedAt() == null) {
            conversation.setCreatedAt(now);
        }
        if (conversation.getUpdatedAt() == null) {
            conversation.setUpdatedAt(now);
        }
        return conversation;
    }

    private RList<ChatMessage> messageList(String conversationId) {
        return redissonClient.getList(keyFactory.messagesKey(conversationId), messageCodec());
    }

    private TypedJsonJacksonCodec messageCodec() {
        if (messageCodec == null) {
            messageCodec = new TypedJsonJacksonCodec(ChatMessage.class, objectMapper);
        }
        return messageCodec;
    }

    private void expireMessages(RList<ChatMessage> list) {
        Duration ttl = chatProperties.getRedis().getConversationTtl();
        if (ttl != null && !ttl.isNegative() && !ttl.isZero()) {
            list.expire(ttl);
        }
    }
}


