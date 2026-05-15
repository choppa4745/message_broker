package com.pigeonmq.domain;

import java.time.Instant;
import java.util.UUID;

public record PendingDelivery(
        UUID messageId,
        String destination,
        DestinationType destType,
        long offset,
        Instant sentAt
) {}
