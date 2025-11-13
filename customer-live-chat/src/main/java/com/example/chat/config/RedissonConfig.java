package com.example.chat.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class RedissonConfig {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnMissingBean
    public RedissonClient redissonClient(RedisProperties redisProperties) {
        Config config = new Config();
        config.useSingleServer()
                .setAddress(buildAddress(redisProperties))
                .setDatabase(redisProperties.getDatabase())
                .setUsername(redisProperties.getUsername())
                .setPassword(
                        StringUtils.hasText(redisProperties.getPassword())
                                ? redisProperties.getPassword()
                                : null);
        return Redisson.create(config);
    }

    private String buildAddress(RedisProperties redisProperties) {
        boolean sslEnabled = redisProperties.getSsl() != null && redisProperties.getSsl().isEnabled();
        String scheme = sslEnabled ? "rediss://" : "redis://";
        return scheme + redisProperties.getHost() + ":" + redisProperties.getPort();
    }
}


