package com.example.chat;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CustomerLiveChatApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomerLiveChatApplication.class, args);
    }
}

