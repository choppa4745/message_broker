package com.pigeonmq.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateQueueRequest(
        @NotBlank(message = "Queue name is required")
        String name,
        String mode
) {}
