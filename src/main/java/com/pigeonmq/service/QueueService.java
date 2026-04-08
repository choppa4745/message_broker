package com.pigeonmq.service;

import com.pigeonmq.domain.DestinationType;
import com.pigeonmq.domain.Message;
import com.pigeonmq.domain.QueueMode;
import com.pigeonmq.domain.QueueStore;
import com.pigeonmq.exception.InvalidRequestException;
import com.pigeonmq.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    private final ConcurrentMap<String, QueueStore> queues = new ConcurrentHashMap<>();

    public QueueStore create(String name, QueueMode mode) {
        validateName(name);
        return queues.computeIfAbsent(name, n -> {
            log.info("Queue created: {} (mode={})", n, mode);
            return new QueueStore(n, mode);
        });
    }

    public QueueStore getOrThrow(String name) {
        QueueStore store = queues.get(name);
        if (store == null) {
            throw new ResourceNotFoundException("Queue", name);
        }
        return store;
    }

    public boolean delete(String name) {
        boolean removed = queues.remove(name) != null;
        if (removed) {
            log.info("Queue deleted: {}", name);
        }
        return removed;
    }

    public Collection<QueueStore> getAll() {
        return Collections.unmodifiableCollection(queues.values());
    }

    public boolean exists(String name) {
        return queues.containsKey(name);
    }

    public long enqueue(String queueName, byte[] payload, int priority) {
        QueueStore store = queues.get(queueName);
        if (store == null) {
            log.warn("Publish to non-existent queue: {}", queueName);
            throw new ResourceNotFoundException("Queue", queueName);
        }
        Message message = Message.create(queueName, DestinationType.QUEUE, payload, priority);
        long offset = store.enqueue(message);
        log.debug("Enqueued to queue {} at offset {}", queueName, offset);
        return offset;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidRequestException("Queue name must not be blank");
        }
    }
}
