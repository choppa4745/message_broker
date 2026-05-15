package com.pigeonmq.sdk.admin.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MutationResponse(
        boolean success,
        String name,
        String message) {}
