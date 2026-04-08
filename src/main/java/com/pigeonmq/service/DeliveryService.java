package com.pigeonmq.service;

import com.pigeonmq.config.BrokerProperties;
import com.pigeonmq.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class DeliveryService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryService.class);

    private final TopicService topicService;
    private final QueueService queueService;
    private final SessionService sessionService;
    private final BrokerProperties properties;

    private final Map<String, AtomicInteger> queueRoundRobin = new ConcurrentHashMap<>();

    private ScheduledExecutorService scheduler;

    public DeliveryService(TopicService topicService,
                           QueueService queueService,
                           SessionService sessionService,
                           BrokerProperties properties) {
        this.topicService = topicService;
        this.queueService = queueService;
        this.sessionService = sessionService;
        this.properties = properties;
    }

    @PostConstruct
    void startScheduler() {
        scheduler = Executors.newScheduledThreadPool(
                properties.getWorkerPoolSize(),
                r -> {
                    Thread t = new Thread(r, "delivery-worker");
                    t.setDaemon(true);
                    return t;
                });

        scheduler.scheduleWithFixedDelay(
                this::deliveryLoop,
                properties.getDeliveryIntervalMs(),
                properties.getDeliveryIntervalMs(),
                TimeUnit.MILLISECONDS);

        log.info("Delivery scheduler started (interval={}ms, workers={})",
                properties.getDeliveryIntervalMs(), properties.getWorkerPoolSize());
    }

    @PreDestroy
    void stopScheduler() {
        if (scheduler != null) {
            scheduler.shutdown();
            log.info("Delivery scheduler stopped");
        }
    }

    // ── Immediate delivery (called after publish) ──────────────────────

    public void deliverTopicMessages(String topicName) {
        TopicStore topic = findTopicOrWarn(topicName);
        if (topic == null) return;

        sessionService.getActiveSessions().stream()
                .filter(s -> s.getSubscribedTopics().contains(topicName))
                .forEach(s -> fanOutToSubscriber(s, topic));
    }

    public void deliverQueueMessages(String queueName) {
        QueueStore queue = findQueueOrWarn(queueName);
        if (queue == null) return;

        List<ClientSession> subscribers = sessionService.getActiveSessions().stream()
                .filter(s -> s.getSubscribedQueues().contains(queueName))
                .toList();

        if (subscribers.isEmpty()) return;
        deliverNextQueueMessage(queue, subscribers);
    }

    // ── Periodic sweep ─────────────────────────────────────────────────

    private void deliveryLoop() {
        try {
            for (TopicStore topic : topicService.getAll()) {
                deliverTopicMessages(topic.getName());
            }
            for (QueueStore queue : queueService.getAll()) {
                deliverQueueMessages(queue.getName());
            }
        } catch (Exception e) {
            log.error("Delivery loop error", e);
        }
    }

    // ── Fan-out: every subscriber gets every message ───────────────────

    private void fanOutToSubscriber(ClientSession session, TopicStore topic) {
        long fromOffset = session.getTopicSentOffset(topic.getName());
        List<Message> pending = topic.getMessagesFrom(fromOffset);

        for (Message msg : pending) {
            int packetId = session.nextPacketId();
            PendingDelivery delivery = new PendingDelivery(
                    msg.id(), topic.getName(), DestinationType.TOPIC, msg.offset(), java.time.Instant.now());
            session.trackDelivery(packetId, delivery);
            session.sendPublish(topic.getName(), msg.payload(), packetId);
            session.advanceTopicSentOffset(topic.getName(), msg.offset() + 1);
        }
    }

    // ── Competing consumers: round-robin one message at a time ─────────

    private void deliverNextQueueMessage(QueueStore queue, List<ClientSession> subscribers) {
        Message msg = queue.claim();
        if (msg == null) return;

        ClientSession target = pickNextSubscriber(queue.getName(), subscribers);
        int packetId = target.nextPacketId();
        PendingDelivery delivery = new PendingDelivery(
                msg.id(), queue.getName(), DestinationType.QUEUE, msg.offset(), java.time.Instant.now());
        target.trackDelivery(packetId, delivery);
        target.sendPublish(queue.getName(), msg.payload(), packetId);
    }

    private ClientSession pickNextSubscriber(String queueName, List<ClientSession> subscribers) {
        AtomicInteger counter = queueRoundRobin.computeIfAbsent(queueName, k -> new AtomicInteger(0));
        int idx = Math.abs(counter.getAndIncrement()) % subscribers.size();
        return subscribers.get(idx);
    }

    // ── Helpers ─────────────────────────────────────────────────────────

    private TopicStore findTopicOrWarn(String name) {
        try {
            return topicService.getOrThrow(name);
        } catch (Exception e) {
            return null;
        }
    }

    private QueueStore findQueueOrWarn(String name) {
        try {
            return queueService.getOrThrow(name);
        } catch (Exception e) {
            return null;
        }
    }
}
