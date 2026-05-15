package com.pigeonmq.service;

import com.pigeonmq.domain.DestinationType;
import com.pigeonmq.domain.Message;
import com.pigeonmq.domain.TopicStore;
import com.pigeonmq.exception.InvalidRequestException;
import com.pigeonmq.exception.ResourceNotFoundException;
import com.pigeonmq.persistence.entity.DestinationEntity;
import com.pigeonmq.persistence.entity.MessageEntity;
import com.pigeonmq.persistence.entity.MessageStatus;
import com.pigeonmq.persistence.repository.DestinationRepository;
import com.pigeonmq.persistence.repository.MessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class TopicService {

    private static final Logger log = LoggerFactory.getLogger(TopicService.class);

    private final ConcurrentMap<String, TopicStore> topics = new ConcurrentHashMap<>();
    private final DestinationRepository destinationRepository;
    private final MessageRepository messageRepository;

    public TopicService(DestinationRepository destinationRepository, MessageRepository messageRepository) {
        this.destinationRepository = destinationRepository;
        this.messageRepository = messageRepository;
    }

    @PostConstruct
    void restoreFromDatabase() {
        List<DestinationEntity> destinations = destinationRepository.findAll().stream()
                .filter(d -> d.getType() == DestinationType.TOPIC)
                .toList();

        for (DestinationEntity d : destinations) {
            TopicStore store = topics.computeIfAbsent(d.getName(), TopicStore::new);
            List<MessageEntity> messages = messageRepository
                    .findByDestinationNameAndDestTypeAndStatusInOrderByOffsetValAsc(
                            d.getName(),
                            DestinationType.TOPIC,
                            List.of(MessageStatus.READY, MessageStatus.INFLIGHT, MessageStatus.ACKED)
                    );
            for (MessageEntity me : messages) {
                Message m = new Message(
                        me.getId(),
                        me.getDestinationName(),
                        me.getDestType(),
                        me.getPayload(),
                        java.util.Map.of(),
                        me.getPriority(),
                        me.getOffsetVal(),
                        me.getCreatedAt()
                );
                store.restore(m);
            }
        }

        if (!destinations.isEmpty()) {
            log.info("Restored topics from DB: {}", destinations.size());
        }
    }

    @Transactional
    public TopicStore create(String name) {
        validateName(name, "Topic");
        return topics.computeIfAbsent(name, n -> {
            destinationRepository.save(new DestinationEntity(n, DestinationType.TOPIC, null, Instant.now()));
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
            destinationRepository.save(new DestinationEntity(n, DestinationType.TOPIC, null, Instant.now()));
            return new TopicStore(n);
        });
    }

    @Transactional
    public boolean delete(String name) {
        boolean removed = topics.remove(name) != null;
        if (removed) {
            destinationRepository.deleteById(name);
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

    @Transactional
    public long publish(String topicName, byte[] payload, int priority) {
        TopicStore store = getOrCreate(topicName);
        Message message = Message.create(topicName, DestinationType.TOPIC, payload, priority);
        long offset = store.publish(message);

        MessageEntity entity = new MessageEntity(
                message.id(),
                topicName,
                DestinationType.TOPIC,
                payload,
                priority,
                offset,
                MessageStatus.READY,
                message.createdAt()
        );
        messageRepository.save(entity);

        log.debug("Published to topic {} at offset {}", topicName, offset);
        return offset;
    }

    private void validateName(String name, String entity) {
        if (name == null || name.isBlank()) {
            throw new InvalidRequestException(entity + " name must not be blank");
        }
    }
}
