package com.pigeonmq.core;

import com.pigeonmq.model.Message;
import com.pigeonmq.model.QueueMode;

import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

public class QueueManager {

    private final String name;
    private final QueueMode mode;
    private final Instant createdAt;
    private final AtomicLong nextOffset = new AtomicLong(0);
    private final Queue<Message> readyMessages;
    private final Map<UUID, Message> inflightMessages = new ConcurrentHashMap<>();

    public QueueManager(String name, QueueMode mode) {
        this.name = name;
        this.mode = mode;
        this.createdAt = Instant.now();

        if (mode == QueueMode.PRIORITY) {
            this.readyMessages = new PriorityBlockingQueue<>(64,
                    Comparator.comparingInt(Message::priority).reversed()
                            .thenComparingLong(Message::offset));
        } else {
            this.readyMessages = new ConcurrentLinkedQueue<>();
        }
    }

    public String getName() { return name; }
    public QueueMode getMode() { return mode; }
    public Instant getCreatedAt() { return createdAt; }
    public int getReadyCount() { return readyMessages.size(); }
    public int getInflightCount() { return inflightMessages.size(); }

    public long enqueue(Message message) {
        long offset = nextOffset.getAndIncrement();
        readyMessages.offer(message.withOffset(offset));
        return offset;
    }

    public synchronized Message claim() {
        Message msg = readyMessages.poll();
        if (msg != null) {
            inflightMessages.put(msg.id(), msg);
        }
        return msg;
    }

    public void ack(UUID messageId) {
        inflightMessages.remove(messageId);
    }

    public void nack(UUID messageId) {
        Message msg = inflightMessages.remove(messageId);
        if (msg != null) {
            readyMessages.offer(msg);
        }
    }
}
