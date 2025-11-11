package com.example.chat.config;

import com.corundumstudio.socketio.protocol.JacksonJsonSupport;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

/**
 * Bridges Spring's configured {@link ObjectMapper} into Netty-SocketIO so that
 * Java time types (Instant, etc.) serialize using the shared Jackson setup.
 */
public class SpringJacksonJsonSupport extends JacksonJsonSupport {

    public SpringJacksonJsonSupport(ObjectMapper baseMapper) {
        super(new JavaTimeModule());

        if (!baseMapper.isEnabled(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)) {
            this.objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        }
        if (!baseMapper.isEnabled(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)) {
            this.objectMapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
        }
        this.objectMapper.setTimeZone(baseMapper.getSerializationConfig().getTimeZone());
        if (baseMapper.getDateFormat() != null) {
            this.objectMapper.setDateFormat(baseMapper.getDateFormat());
        }
    }
}
