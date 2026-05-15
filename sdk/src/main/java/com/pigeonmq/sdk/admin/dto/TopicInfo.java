package com.pigeonmq.sdk.admin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record TopicInfo(
        String name,
        long messageCount,
        long latestOffset,
        String createdAt) {}
