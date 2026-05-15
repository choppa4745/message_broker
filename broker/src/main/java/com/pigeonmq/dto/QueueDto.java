package com.pigeonmq.dto;

import com.pigeonmq.domain.QueueStore;

public record QueueDto(
        String name,
        String mode,
        int readyCount,
        int inflightCount,
        String createdAt
) {

    public static QueueDto from(QueueStore store) {
        return new QueueDto(
                store.getName(),
                store.getMode().name(),
                store.getReadyCount(),
                store.getInflightCount(),
                store.getCreatedAt().toString());
    }
}
