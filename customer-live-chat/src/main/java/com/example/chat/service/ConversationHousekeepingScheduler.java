package com.example.chat.service;

import com.example.chat.config.ChatProperties;
import com.example.chat.domain.ConversationMetadata;
import com.example.chat.domain.ConversationStatus;
import com.example.chat.domain.QueueEntry;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class ConversationHousekeepingScheduler {

    private final ChatProperties chatProperties;
    private final ConversationRepository conversationRepository;
    private final ConversationService conversationService;
    private final AgentQueueService agentQueueService;

    @Scheduled(fixedDelayString = "#{T(java.time.Duration).parse('${chat.housekeeping.interval:PT1M}').toMillis()}")
    public void enforceLimits() {
        enforceQueueTtl();
        enforceConversationTtl();
    }

    private void enforceQueueTtl() {
        Duration ttl = chatProperties.getQueue().getEntryTtl();
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        List<QueueEntry> purged = agentQueueService.purgeOlderThan(ttl);
        if (CollectionUtils.isEmpty(purged)) {
            return;
        }
        for (QueueEntry entry : purged) {
            try {
                conversationService
                        .getConversation(entry.getConversationId())
                        .filter(metadata -> metadata.getStatus() != ConversationStatus.CLOSED)
                        .ifPresent(metadata -> {
                            log.debug(
                                    "Closing conversation {} due to stale queue entry (enqueued at {})",
                                    metadata.getId(),
                                    entry.getEnqueuedAt());
                            conversationService.closeConversation(metadata.getId(), null);
                        });
            } catch (Exception ex) {
                log.warn("Failed to close stale queued conversation {}", entry.getConversationId(), ex);
            }
        }
    }

    private void enforceConversationTtl() {
        List<ConversationMetadata> conversations = conversationRepository.findAll();
        if (CollectionUtils.isEmpty(conversations)) {
            return;
        }

        Duration inactivityTimeout = chatProperties.getConversation().getInactivityTimeout();
        Duration maxDuration = chatProperties.getConversation().getMaxDuration();
        Instant now = Instant.now();
        Instant inactivityCutoff = computeCutoff(now, inactivityTimeout);
        Instant maxDurationCutoff = computeCutoff(now, maxDuration);

        for (ConversationMetadata conversation : conversations) {
            if (conversation.getStatus() == ConversationStatus.CLOSED) {
                continue;
            }

            boolean shouldClose = false;
            Instant lastActivity = conversation.getUpdatedAt() != null
                    ? conversation.getUpdatedAt()
                    : conversation.getCreatedAt();

            if (lastActivity != null && isExceeded(inactivityTimeout, inactivityCutoff, lastActivity)) {
                shouldClose = true;
            }

            if (!shouldClose && conversation.getCreatedAt() != null
                    && isExceeded(maxDuration, maxDurationCutoff, conversation.getCreatedAt())) {
                shouldClose = true;
            }

            if (!shouldClose) {
                continue;
            }

            try {
                log.debug("Automatically closing conversation {} due to inactivity/TTL thresholds", conversation.getId());
                conversationService.closeConversation(conversation.getId(), null);
            } catch (IllegalArgumentException ignored) {
                log.trace("Conversation {} already removed before housekeeping processed it", conversation.getId());
            } catch (Exception ex) {
                log.warn("Failed to automatically close conversation {}", conversation.getId(), ex);
            }
        }
    }

    private boolean isExceeded(Duration threshold, Instant cutoff, Instant reference) {
        return threshold != null
                && cutoff != null
                && !threshold.isZero()
                && !threshold.isNegative()
                && reference.isBefore(cutoff);
    }

    private Instant computeCutoff(Instant now, Duration threshold) {
        if (threshold == null || threshold.isZero() || threshold.isNegative()) {
            return null;
        }
        return now.minus(threshold);
    }
}

