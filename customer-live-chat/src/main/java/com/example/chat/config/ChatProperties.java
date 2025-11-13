package com.example.chat.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "chat")
public class ChatProperties {

    private String namespace = "livechat";

    @NestedConfigurationProperty
    private final Redis redis = new Redis();

    @NestedConfigurationProperty
    private final Kafka kafka = new Kafka();

    @NestedConfigurationProperty
    private final Queue queue = new Queue();

    @NestedConfigurationProperty
    private final Conversation conversation = new Conversation();

    @NestedConfigurationProperty
    private final Housekeeping housekeeping = new Housekeeping();

    public String getNamespace() {
        return namespace;
    }

    public void setNamespace(String namespace) {
        this.namespace = namespace;
    }

    public Redis getRedis() {
        return redis;
    }

    public Kafka getKafka() {
        return kafka;
    }

    public Queue getQueue() {
        return queue;
    }

    public Conversation getConversation() {
        return conversation;
    }

    public Housekeeping getHousekeeping() {
        return housekeeping;
    }

    @Validated
    public static class Redis {

        /**
         * Prefix applied to all Redis keys controlled by the chat module.
         */
        private String keyPrefix = "chat";

        /**
         * Redis key time-to-live for inactive conversations.
         */
        private Duration conversationTtl = Duration.ofHours(24);

        /**
         * Redis key time-to-live for presence entries.
         */
        private Duration presenceTtl = Duration.ofMinutes(5);

        public String getKeyPrefix() {
            return keyPrefix;
        }

        public void setKeyPrefix(String keyPrefix) {
            this.keyPrefix = keyPrefix;
        }

        public Duration getConversationTtl() {
            return conversationTtl;
        }

        public void setConversationTtl(Duration conversationTtl) {
            this.conversationTtl = conversationTtl;
        }

        public Duration getPresenceTtl() {
            return presenceTtl;
        }

        public void setPresenceTtl(Duration presenceTtl) {
            this.presenceTtl = presenceTtl;
        }
    }

    @Validated
    public static class Kafka {

        /**
         * Kafka topic to publish chat lifecycle events.
         */
        private String lifecycleTopic = "chat.lifecycle";

        /**
         * Kafka topic to publish chat message events.
         */
        private String messageTopic = "chat.messages";

        public String getLifecycleTopic() {
            return lifecycleTopic;
        }

        public void setLifecycleTopic(String lifecycleTopic) {
            this.lifecycleTopic = lifecycleTopic;
        }

        public String getMessageTopic() {
            return messageTopic;
        }

        public void setMessageTopic(String messageTopic) {
            this.messageTopic = messageTopic;
        }
    }

    @Validated
    public static class Queue {

        /**
         * Maximum amount of time a customer can remain in the queue without an agent assignment.
         */
        private Duration maxWait = Duration.ofMinutes(10);

        /**
         * Maximum number of conversations an agent can accept simultaneously.
         */
        private int maxConcurrentByAgent = 3;

        /**
         * Maximum age of a queue entry before it is purged.
         */
        private Duration entryTtl = Duration.ofMinutes(30);

        /**
         * Maximum number of queue entries to broadcast to connected agents.
         */
        private int broadcastLimit = 100;

        public Duration getMaxWait() {
            return maxWait;
        }

        public void setMaxWait(Duration maxWait) {
            this.maxWait = maxWait;
        }

        public int getMaxConcurrentByAgent() {
            return maxConcurrentByAgent;
        }

        public void setMaxConcurrentByAgent(int maxConcurrentByAgent) {
            this.maxConcurrentByAgent = maxConcurrentByAgent;
        }

        public Duration getEntryTtl() {
            return entryTtl;
        }

        public void setEntryTtl(Duration entryTtl) {
            this.entryTtl = entryTtl;
        }

        public int getBroadcastLimit() {
            return broadcastLimit;
        }

        public void setBroadcastLimit(int broadcastLimit) {
            this.broadcastLimit = broadcastLimit;
        }
    }

    @Validated
    public static class Conversation {

        /**
         * Duration after the last activity when a conversation should be closed automatically.
         */
        private Duration inactivityTimeout = Duration.ofMinutes(30);

        /**
         * Absolute maximum lifetime for an open conversation, independent of activity.
         */
        private Duration maxDuration = Duration.ofHours(12);

        public Duration getInactivityTimeout() {
            return inactivityTimeout;
        }

        public void setInactivityTimeout(Duration inactivityTimeout) {
            this.inactivityTimeout = inactivityTimeout;
        }

        public Duration getMaxDuration() {
            return maxDuration;
        }

        public void setMaxDuration(Duration maxDuration) {
            this.maxDuration = maxDuration;
        }
    }

    @Validated
    public static class Housekeeping {

        /**
         * Interval between automatic housekeeping cycles.
         */
        private Duration interval = Duration.ofMinutes(1);

        public Duration getInterval() {
            return interval;
        }

        public void setInterval(Duration interval) {
            this.interval = interval;
        }
    }
}

