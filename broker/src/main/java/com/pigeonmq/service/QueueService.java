package com.pigeonmq.service;

import com.pigeonmq.domain.DestinationType;
import com.pigeonmq.domain.Message;
import com.pigeonmq.domain.QueueMode;
import com.pigeonmq.domain.QueueStore;
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
public class QueueService {

    private static final Logger log = LoggerFactory.getLogger(QueueService.class);

    private final ConcurrentMap<String, QueueStore> queues = new ConcurrentHashMap<>();
    private final DestinationRepository destinationRepository;
    private final MessageRepository messageRepository;

    public QueueService(DestinationRepository destinationRepository, MessageRepository messageRepository) {
        this.destinationRepository = destinationRepository;
        this.messageRepository = messageRepository;
    }

    @PostConstruct
    void restoreFromDatabase() {
        List<DestinationEntity> destinations = destinationRepository.findAll().stream()
                .filter(d -> d.getType() == DestinationType.QUEUE)
                .toList();

        for (DestinationEntity d : destinations) {
            QueueMode mode = d.getMode() != null ? d.getMode() : QueueMode.FIFO;
            QueueStore store = queues.computeIfAbsent(d.getName(), n -> new QueueStore(n, mode));

            List<MessageEntity> messages = messageRepository
                    .findByDestinationNameAndDestTypeAndStatusInOrderByOffsetValAsc(
                            d.getName(),
                            DestinationType.QUEUE,
                            List.of(MessageStatus.READY, MessageStatus.INFLIGHT)
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
                // at-least-once: any INFLIGHT becomes READY on restart
                store.restoreReady(m);
                if (me.getStatus() == MessageStatus.INFLIGHT) {
                    me.setStatus(MessageStatus.READY);
                    messageRepository.save(me);
                }
            }
        }

        if (!destinations.isEmpty()) {
            log.info("Restored queues from DB: {}", destinations.size());
        }
    }

    @Transactional
    public QueueStore create(String name, QueueMode mode) {
        validateName(name);
        return queues.computeIfAbsent(name, n -> {
            destinationRepository.save(new DestinationEntity(n, DestinationType.QUEUE, mode, Instant.now()));
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

    @Transactional
    public boolean delete(String name) {
        boolean removed = queues.remove(name) != null;
        if (removed) {
            destinationRepository.deleteById(name);
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

    @Transactional
    public long enqueue(String queueName, byte[] payload, int priority) {
        QueueStore store = queues.get(queueName);
        if (store == null) {
            log.warn("Publish to non-existent queue: {}", queueName);
            throw new ResourceNotFoundException("Queue", queueName);
        }
        Message message = Message.create(queueName, DestinationType.QUEUE, payload, priority);
        long offset = store.enqueue(message);

        MessageEntity entity = new MessageEntity(
                message.id(),
                queueName,
                DestinationType.QUEUE,
                payload,
                priority,
                offset,
                MessageStatus.READY,
                message.createdAt()
        );
        messageRepository.save(entity);

        log.debug("Enqueued to queue {} at offset {}", queueName, offset);
        return offset;
    }

    private void validateName(String name) {
        if (name == null || name.isBlank()) {
            throw new InvalidRequestException("Queue name must not be blank");
        }
    }
}
