package com.pigeonmq.sdk.admin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record HealthInfo(
        String status,
        int topicCount,
        int queueCount,
        int activeConnections) {}
