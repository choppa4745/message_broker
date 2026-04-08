package com.pigeonmq.core;

import com.pigeonmq.model.Message;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.atomic.AtomicLong;

public class TopicManager {

    private final String name;
    private final Instant createdAt;
    private final AtomicLong nextOffset = new AtomicLong(0);
    private final ConcurrentNavigableMap<Long, Message> messages = new ConcurrentSkipListMap<>();

    public TopicManager(String name) {
        this.name = name;
        this.createdAt = Instant.now();
    }

    public String getName() { return name; }
    public Instant getCreatedAt() { return createdAt; }
    public long getMessageCount() { return messages.size(); }
    public long getLatestOffset() { return nextOffset.get(); }

    public long publish(Message message) {
        long offset = nextOffset.getAndIncrement();
        messages.put(offset, message.withOffset(offset));
        return offset;
    }

    public List<Message> getMessagesFrom(long fromOffset) {
        return new ArrayList<>(messages.tailMap(fromOffset).values());
    }
}
