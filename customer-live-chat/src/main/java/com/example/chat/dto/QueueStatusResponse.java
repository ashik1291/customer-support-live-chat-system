package com.example.chat.dto;

import java.time.Duration;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class QueueStatusResponse {
    long position;
    Duration estimatedWait;
}

