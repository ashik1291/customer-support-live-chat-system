package com.example.chat.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "chat.security")
public class ChatSecurityProperties {

    /**
     * Toggle to enable or disable the inbound HTTP rate limiter.
     */
    private boolean rateLimitingEnabled = true;

    private final RateLimit rateLimit = new RateLimit();

    public boolean isRateLimitingEnabled() {
        return rateLimitingEnabled;
    }

    public void setRateLimitingEnabled(boolean rateLimitingEnabled) {
        this.rateLimitingEnabled = rateLimitingEnabled;
    }

    public RateLimit getRateLimit() {
        return rateLimit;
    }

    @Validated
    public static class RateLimit {

        /**
         * Maximum number of requests allowed per refill period.
         */
        private long capacity = 200;

        /**
         * Number of tokens replenished every {@link #refillPeriod}.
         */
        private long refillTokens = 200;

        /**
         * Interval at which tokens are replenished.
         */
        private Duration refillPeriod = Duration.ofSeconds(60);

        public long getCapacity() {
            return capacity;
        }

        public void setCapacity(long capacity) {
            this.capacity = capacity;
        }

        public long getRefillTokens() {
            return refillTokens;
        }

        public void setRefillTokens(long refillTokens) {
            this.refillTokens = refillTokens;
        }

        public Duration getRefillPeriod() {
            return refillPeriod;
        }

        public void setRefillPeriod(Duration refillPeriod) {
            this.refillPeriod = refillPeriod;
        }
    }
}

