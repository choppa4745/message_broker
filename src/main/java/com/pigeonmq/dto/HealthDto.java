package com.pigeonmq.dto;

public record HealthDto(
        String status,
        int topicCount,
        int queueCount,
        int activeConnections
) {}
