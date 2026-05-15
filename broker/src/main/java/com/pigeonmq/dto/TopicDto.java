package com.pigeonmq.dto;

import com.pigeonmq.domain.TopicStore;

public record TopicDto(
        String name,
        long messageCount,
        long latestOffset,
        String createdAt
) {

    public static TopicDto from(TopicStore store) {
        return new TopicDto(
                store.getName(),
                store.getMessageCount(),
                store.getLatestOffset(),
                store.getCreatedAt().toString());
    }
}
