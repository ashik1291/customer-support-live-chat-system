package com.example.chat.service;

import com.example.chat.config.ChatProperties;
import com.example.chat.domain.QueueEntry;
import com.example.chat.dto.QueueSnapshotPayload;
import com.example.chat.service.exception.ServiceException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.codec.TypedJsonJacksonCodec;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class AgentQueueService {

    private final RedissonClient redissonClient;
    private final RedisKeyFactory keyFactory;
    private final ChatProperties chatProperties;
    private final QueueEventPublisher queueEventPublisher;
    private final ObjectMapper objectMapper;

    private TypedJsonJacksonCodec entryCodec;

    private TypedJsonJacksonCodec entryCodec() {
        if (entryCodec == null) {
            entryCodec = new TypedJsonJacksonCodec(String.class, QueueEntry.class, objectMapper);
        }
        return entryCodec;
    }

    public void enqueue(QueueEntry entry) {
        if (entry == null || !StringUtils.hasText(entry.getConversationId())) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "Queue entry must include a conversation id");
        }
        QueueEntry normalized = normalizeEntry(entry);
        RLock lock = queueLock();
        lock.lock();
        try {
            queueEntries().fastPut(normalized.getConversationId(), normalized);
            orderedQueue().add(normalized.getEnqueuedAt().toEpochMilli(), normalized.getConversationId());
            publishSnapshot();
        } finally {
            lock.unlock();
        }
    }

    public ClaimResult claimForAgent(String conversationId, String agentId, Duration assignmentTtl) {
        validateConversation(conversationId);
        RLock lock = queueLock();
        lock.lock();
        try {
            RBucket<String> assignment = assignmentBucket(conversationId);
            String owner = assignment.get();
            if (StringUtils.hasText(owner) && !owner.equals(agentId)) {
                return new ClaimResult(ClaimStatus.BUSY, Optional.empty());
            }

            QueueEntry entry = queueEntries().get(conversationId);
            if (entry == null) {
                if (StringUtils.hasText(owner) && owner.equals(agentId)) {
                    refreshAssignmentTtl(assignment, assignmentTtl);
                    return new ClaimResult(ClaimStatus.OWNED, Optional.empty());
                }
                orderedQueue().remove(conversationId);
                return new ClaimResult(ClaimStatus.MISSING, Optional.empty());
            }

            queueEntries().fastRemove(conversationId);
            orderedQueue().remove(conversationId);
            assignment.set(agentId);
            refreshAssignmentTtl(assignment, assignmentTtl);
            publishSnapshot();
            return new ClaimResult(ClaimStatus.CLAIMED, Optional.of(entry));
        } finally {
            lock.unlock();
        }
    }

    public Optional<QueueEntry> peek() {
        Collection<String> ids = orderedQueue().valueRange(0, 0);
        if (ids == null || ids.isEmpty()) {
            return Optional.empty();
        }
        String firstId = ids.iterator().next();
        QueueEntry entry = queueEntries().get(firstId);
        if (entry == null) {
            orderedQueue().remove(firstId);
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    public Optional<QueueEntry> remove(String conversationId) {
        validateConversation(conversationId);
        RLock lock = queueLock();
        lock.lock();
        try {
            QueueEntry entry = queueEntries().get(conversationId);
            if (entry != null) {
                queueEntries().fastRemove(conversationId);
                orderedQueue().remove(conversationId);
                publishSnapshot();
                return Optional.of(entry);
            }
            orderedQueue().remove(conversationId);
            return Optional.empty();
        } finally {
            lock.unlock();
        }
    }

    public void touch(String conversationId) {
        validateConversation(conversationId);
        RLock lock = queueLock();
        lock.lock();
        try {
            QueueEntry existing = queueEntries().get(conversationId);
            if (existing == null) {
                orderedQueue().remove(conversationId);
                return;
            }
            QueueEntry updated = QueueEntry.builder()
                    .conversationId(existing.getConversationId())
                    .customerId(existing.getCustomerId())
                    .customerName(existing.getCustomerName())
                    .customerPhone(existing.getCustomerPhone())
                    .channel(existing.getChannel())
                    .enqueuedAt(Instant.now())
                    .build();
            queueEntries().fastPut(conversationId, updated);
            orderedQueue().add(updated.getEnqueuedAt().toEpochMilli(), conversationId);
            publishSnapshot();
        } finally {
            lock.unlock();
        }
    }

    public List<QueueEntry> purgeOlderThan(Duration ttl) {
        if (ttl == null || ttl.isZero() || ttl.isNegative()) {
            return List.of();
        }
        long cutoffScore = Instant.now().minus(ttl).toEpochMilli();
        List<QueueEntry> removed = new ArrayList<>();
        RLock lock = queueLock();
        lock.lock();
        try {
            Collection<String> expiredIds = orderedQueue()
                    .valueRange(Double.NEGATIVE_INFINITY, true, (double) cutoffScore, true);
            if (CollectionUtils.isEmpty(expiredIds)) {
                return List.of();
            }
            for (String id : expiredIds) {
                QueueEntry entry = queueEntries().remove(id);
                if (entry != null) {
                    removed.add(entry);
                }
            }
            if (!expiredIds.isEmpty()) {
                orderedQueue().removeAll(expiredIds);
                publishSnapshot();
            }
        } finally {
            lock.unlock();
        }
        return removed;
    }

    public List<QueueEntry> listQueue(int limit) {
        return listQueue(0, limit);
    }

    public long position(String conversationId) {
        validateConversation(conversationId);
        Integer rank = orderedQueue().rank(conversationId);
        return rank != null ? rank : -1;
    }

    public List<QueueEntry> listQueue(int page, int size) {
        int pageSize = Math.max(size, 0);
        if (pageSize == 0) {
            return List.of();
        }
        int offset = Math.max(page, 0) * pageSize;
        Collection<String> ids = orderedQueue().valueRange(offset, offset + pageSize - 1);
        if (CollectionUtils.isEmpty(ids)) {
            return List.of();
        }
        Map<String, QueueEntry> entriesById = new LinkedHashMap<>(queueEntries().getAll(new HashSet<>(ids)));
        List<QueueEntry> orderedEntries = new ArrayList<>(ids.size());
        for (String id : ids) {
            QueueEntry entry = entriesById.get(id);
            if (entry != null) {
                orderedEntries.add(entry);
            } else {
                orderedQueue().remove(id);
            }
        }
        return orderedEntries;
    }

    private QueueEntry normalizeEntry(QueueEntry entry) {
        Instant now = entry.getEnqueuedAt() != null ? entry.getEnqueuedAt() : Instant.now();
        return QueueEntry.builder()
                .conversationId(entry.getConversationId())
                .customerId(entry.getCustomerId())
                .customerName(entry.getCustomerName())
                .customerPhone(entry.getCustomerPhone())
                .channel(entry.getChannel())
                .enqueuedAt(now)
                .build();
    }

    private void publishSnapshot() {
        int limit = Math.max(1, chatProperties.getQueue().getBroadcastLimit());
        List<QueueEntry> snapshot = listQueue(0, limit);
        queueEventPublisher.publishSnapshot(QueueSnapshotPayload.builder().entries(snapshot).build());
    }

    private RScoredSortedSet<String> orderedQueue() {
        return redissonClient.getScoredSortedSet(keyFactory.queueKey(), StringCodec.INSTANCE);
    }

    private RMap<String, QueueEntry> queueEntries() {
        return redissonClient.getMap(keyFactory.queueEntriesKey(), entryCodec());
    }

    private RBucket<String> assignmentBucket(String conversationId) {
        return redissonClient.getBucket(keyFactory.conversationAssignmentKey(conversationId), StringCodec.INSTANCE);
    }

    private void refreshAssignmentTtl(RBucket<String> bucket, Duration ttl) {
        if (ttl != null && !ttl.isNegative() && !ttl.isZero()) {
            bucket.expire(ttl);
        }
    }

    private RLock queueLock() {
        return redissonClient.getLock(keyFactory.queueLockKey());
    }

    private void validateConversation(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            throw new ServiceException(HttpStatus.BAD_REQUEST, "Conversation id is required");
        }
    }

    public enum ClaimStatus {
        CLAIMED,
        OWNED,
        MISSING,
        BUSY
    }

    public record ClaimResult(ClaimStatus status, Optional<QueueEntry> entry) {}
}

