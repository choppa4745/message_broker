package com.pigeonmq.service;

import com.pigeonmq.domain.DestinationType;
import com.pigeonmq.domain.Message;
import com.pigeonmq.domain.TopicStore;
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
public class TopicService {

    private static final Logger log = LoggerFactory.getLogger(TopicService.class);

    private final ConcurrentMap<String, TopicStore> topics = new ConcurrentHashMap<>();

    public TopicStore create(String name) {
        validateName(name, "Topic");
        return topics.computeIfAbsent(name, n -> {
            log.info("Topic created: {}", n);
            return new TopicStore(n);
        });
    }

    public TopicStore getOrThrow(String name) {
        TopicStore store = topics.get(name);
        if (store == null) {
            throw new ResourceNotFoundException("Topic", name);
        }
        return store;
    }

    public TopicStore getOrCreate(String name) {
        return topics.computeIfAbsent(name, n -> {
            log.debug("Auto-created topic on first publish: {}", n);
            return new TopicStore(n);
        });
    }

    public boolean delete(String name) {
        boolean removed = topics.remove(name) != null;
        if (removed) {
            log.info("Topic deleted: {}", name);
        }
        return removed;
    }

    public Collection<TopicStore> getAll() {
        return Collections.unmodifiableCollection(topics.values());
    }

    public boolean exists(String name) {
        return topics.containsKey(name);
    }

    public long publish(String topicName, byte[] payload, int priority) {
        TopicStore store = getOrCreate(topicName);
        Message message = Message.create(topicName, DestinationType.TOPIC, payload, priority);
        long offset = store.publish(message);
        log.debug("Published to topic {} at offset {}", topicName, offset);
        return offset;
    }

    private void validateName(String name, String entity) {
        if (name == null || name.isBlank()) {
            throw new InvalidRequestException(entity + " name must not be blank");
        }
    }
}
