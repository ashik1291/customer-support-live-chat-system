package com.example.chat.service;

import com.example.chat.config.ChatProperties;
import com.example.chat.domain.ConversationMetadata;
import com.example.chat.domain.ConversationStatus;
import com.example.chat.domain.QueueEntry;
import com.example.chat.service.exception.ServiceException;
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
        Duration inactivityTimeout = chatProperties.getConversation().getInactivityTimeout();
        Duration maxDuration = chatProperties.getConversation().getMaxDuration();
        Instant now = Instant.now();
        Instant inactivityCutoff = computeCutoff(now, inactivityTimeout);
        Instant maxDurationCutoff = computeCutoff(now, maxDuration);
        if (inactivityCutoff == null && maxDurationCutoff == null) {
            return;
        }

        List<ConversationMetadata> conversations = conversationRepository.findStaleConversations(
                inactivityCutoff, maxDurationCutoff);
        if (CollectionUtils.isEmpty(conversations)) {
            return;
        }

        for (ConversationMetadata conversation : conversations) {
            try {
                log.debug("Automatically closing conversation {} due to inactivity/TTL thresholds", conversation.getId());
                conversationService.closeConversation(conversation.getId(), null);
            } catch (ServiceException ex) {
                log.trace("Conversation {} already removed before housekeeping processed it", conversation.getId(), ex);
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

