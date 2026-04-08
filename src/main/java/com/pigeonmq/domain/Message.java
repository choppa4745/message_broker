package com.pigeonmq.domain;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record Message(
        UUID id,
        String destination,
        DestinationType destType,
        byte[] payload,
        Map<String, String> headers,
        int priority,
        long offset,
        Instant createdAt
) {

    public static Message create(String destination, DestinationType destType,
                                 byte[] payload, int priority) {
        return new Message(
                UUID.randomUUID(), destination, destType,
                payload, Map.of(), priority, -1, Instant.now());
    }

    public Message withOffset(long newOffset) {
        return new Message(id, destination, destType, payload, headers, priority, newOffset, createdAt);
    }
}
