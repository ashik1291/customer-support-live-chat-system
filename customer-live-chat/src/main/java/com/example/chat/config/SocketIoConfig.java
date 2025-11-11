package com.example.chat.config;

import com.corundumstudio.socketio.Configuration;
import com.corundumstudio.socketio.SocketIOServer;
import com.corundumstudio.socketio.Transport;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

@org.springframework.context.annotation.Configuration
public class SocketIoConfig implements DisposableBean {

    private SocketIOServer server;

    @Bean
    public SocketIOServer socketIOServer(
            @Value("${chat.socketio.host:0.0.0.0}") String host,
            @Value("${chat.socketio.port:9094}") int port,
            ObjectMapper objectMapper) {
        Configuration configuration = new Configuration();
        configuration.setHostname(host);
        configuration.setPort(port);
        configuration.setAllowCustomRequests(true);
        configuration.setOrigin("*");
        configuration.setTransports(Transport.WEBSOCKET, Transport.POLLING);
        configuration.setJsonSupport(new SpringJacksonJsonSupport(objectMapper));

        server = new SocketIOServer(configuration);
        server.start();
        return server;
    }

    @PreDestroy
    @Override
    public void destroy() {
        if (server != null) {
            server.stop();
        }
    }
}

