package com.pigeonmq.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateTopicRequest(
        @NotBlank(message = "Topic name is required")
        String name
) {}
