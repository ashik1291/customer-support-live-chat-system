package com.example.chat.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info =
                @Info(
                        title = "Customer Live Chat API",
                        version = "1.0",
                        description = "Interactive endpoints for managing conversations, agents and chat flows.",
                        contact = @Contact(name = "Customer Live Chat Team", email = "support@example.com")))
public class OpenApiConfig {

    @Bean
    public GroupedOpenApi chatApi() {
        return GroupedOpenApi.builder()
                .group("chat")
                .pathsToMatch("/api/**")
                .build();
    }
}

