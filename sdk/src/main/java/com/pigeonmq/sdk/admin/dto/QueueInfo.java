package com.pigeonmq.sdk.admin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record QueueInfo(
        String name,
        String mode,
        int readyCount,
        int inflightCount,
        String createdAt) {}
