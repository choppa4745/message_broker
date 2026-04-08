package com.pigeonmq.core;

import com.pigeonmq.config.BrokerConfig;
import com.pigeonmq.model.DestinationType;
import com.pigeonmq.model.Message;
import com.pigeonmq.model.PendingDelivery;
import com.pigeonmq.model.QueueMode;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Broker {

    private static final Logger log = LoggerFactory.getLogger(Broker.class);

    private final BrokerConfig config;
    private final ConcurrentMap<String, TopicManager> topics = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, QueueManager> queues = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, ClientSession> sessions = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, AtomicInteger> queueRoundRobin = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler;

    public Broker(BrokerConfig config) {
        this.config = config;
        this.scheduler = Executors.newScheduledThreadPool(config.getWorkerPoolSize(),
                r -> { Thread t = new Thread(r, "broker-worker"); t.setDaemon(true); return t; });
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::deliveryLoop,
                config.getDeliveryIntervalMs(), config.getDeliveryIntervalMs(), TimeUnit.MILLISECONDS);
        log.info("Broker started (deliveryInterval={}ms)", config.getDeliveryIntervalMs());
    }

    public void stop() {
        scheduler.shutdown();
        try { scheduler.awaitTermination(5, TimeUnit.SECONDS); } catch (InterruptedException ignored) {}
        log.info("Broker stopped");
    }

    /* ═══════════════ Topic CRUD ═══════════════ */

    public TopicManager createTopic(String name) {
        return topics.computeIfAbsent(name, n -> {
            log.info("Topic created: {}", n);
            return new TopicManager(n);
        });
    }

    public boolean deleteTopic(String name) {
        boolean ok = topics.remove(name) != null;
        if (ok) log.info("Topic deleted: {}", name);
        return ok;
    }

    public Map<String, TopicManager> getTopics() { return Collections.unmodifiableMap(topics); }
    public TopicManager getTopic(String name) { return topics.get(name); }

    /* ═══════════════ Queue CRUD ═══════════════ */

    public QueueManager createQueue(String name, QueueMode mode) {
        return queues.computeIfAbsent(name, n -> {
            log.info("Queue created: {} (mode={})", n, mode);
            return new QueueManager(n, mode);
        });
    }

    public boolean deleteQueue(String name) {
        boolean ok = queues.remove(name) != null;
        if (ok) { queueRoundRobin.remove(name); log.info("Queue deleted: {}", name); }
        return ok;
    }

    public Map<String, QueueManager> getQueues() { return Collections.unmodifiableMap(queues); }
    public QueueManager getQueue(String name) { return queues.get(name); }

    /* ═══════════════ Session management ═══════════════ */

    public ClientSession registerClient(String clientId, Channel channel) {
        ClientSession old = sessions.get(clientId);
        if (old != null && old.isActive()) {
            log.warn("Replacing active session for client {}", clientId);
            old.getChannel().close();
        }
        ClientSession session = new ClientSession(clientId, channel);
        sessions.put(clientId, session);
        log.info("Client connected: {}", clientId);
        return session;
    }

    public void removeClient(String clientId) {
        ClientSession removed = sessions.remove(clientId);
        if (removed != null) log.info("Client disconnected: {}", clientId);
    }

    public ClientSession getSession(String clientId) { return sessions.get(clientId); }

    /* ═══════════════ Publish ═══════════════ */

    public void publish(String destination, byte[] payload, Map<String, String> headers, int priority) {
        DestinationType type = resolveDestType(destination);
        Message message = new Message(
                UUID.randomUUID(), destination, type,
                payload, headers != null ? headers : Map.of(),
                priority, -1, Instant.now());

        if (type == DestinationType.QUEUE) {
            QueueManager qm = queues.get(destination);
            if (qm == null) { log.warn("Queue not found: {}", destination); return; }
            long offset = qm.enqueue(message);
            log.debug("Enqueued: queue={}, offset={}, id={}", destination, offset, message.id());
            tryDeliverQueue(destination);
        } else {
            TopicManager tm = topics.computeIfAbsent(destination, TopicManager::new);
            long offset = tm.publish(message);
            log.debug("Published: topic={}, offset={}, id={}", destination, offset, message.id());
            deliverNewTopicMessages(destination);
        }
    }

    /* ═══════════════ Subscribe ═══════════════ */

    public void subscribe(ClientSession session, String destination, DestinationType destType) {
        if (destType == DestinationType.TOPIC) {
            topics.computeIfAbsent(destination, TopicManager::new);
            session.subscribeTopic(destination);
            log.info("{} subscribed to topic '{}'", session.getClientId(), destination);
            deliverPendingTopicMessages(session, destination);
        } else {
            QueueManager qm = queues.get(destination);
            if (qm == null) {
                log.warn("Queue '{}' not found, cannot subscribe {}", destination, session.getClientId());
                return;
            }
            session.subscribeQueue(destination);
            log.info("{} subscribed to queue '{}'", session.getClientId(), destination);
            tryDeliverQueue(destination);
        }
    }

    /* ═══════════════ ACK ═══════════════ */

    public void acknowledgeDelivery(ClientSession session, int packetId) {
        PendingDelivery d = session.completeDelivery(packetId);
        if (d == null) return;

        if (d.destType() == DestinationType.TOPIC) {
            log.debug("Topic ACK: client={}, topic={}, offset={}", session.getClientId(), d.destination(), d.offset());
        } else {
            QueueManager qm = queues.get(d.destination());
            if (qm != null) {
                qm.ack(d.messageId());
                log.debug("Queue ACK: client={}, queue={}, id={}", session.getClientId(), d.destination(), d.messageId());
            }
            tryDeliverQueue(d.destination());
        }
    }

    /* ═══════════════ Internal delivery helpers ═══════════════ */

    private DestinationType resolveDestType(String destination) {
        return queues.containsKey(destination) ? DestinationType.QUEUE : DestinationType.TOPIC;
    }

    private void deliverNewTopicMessages(String topicName) {
        for (ClientSession session : sessions.values()) {
            if (session.isActive() && session.getSubscribedTopics().contains(topicName)) {
                deliverPendingTopicMessages(session, topicName);
            }
        }
    }

    private void deliverPendingTopicMessages(ClientSession session, String topicName) {
        TopicManager tm = topics.get(topicName);
        if (tm == null) return;

        long from = session.getTopicSentOffset(topicName);
        List<Message> pending = tm.getMessagesFrom(from);
        for (Message msg : pending) {
            deliverToClient(session, msg, topicName, DestinationType.TOPIC, msg.offset());
            session.advanceTopicSentOffset(topicName, msg.offset() + 1);
        }
    }

    private void tryDeliverQueue(String queueName) {
        QueueManager qm = queues.get(queueName);
        if (qm == null) return;

        List<ClientSession> subs = getQueueSubscribers(queueName);
        if (subs.isEmpty()) return;

        AtomicInteger rr = queueRoundRobin.computeIfAbsent(queueName, k -> new AtomicInteger(0));
        Message msg;
        while ((msg = qm.claim()) != null) {
            int idx = Math.abs(rr.getAndIncrement() % subs.size());
            ClientSession target = subs.get(idx);
            if (target.isActive()) {
                deliverToClient(target, msg, queueName, DestinationType.QUEUE, msg.offset());
            } else {
                qm.nack(msg.id());
                break;
            }
        }
    }

    private List<ClientSession> getQueueSubscribers(String queueName) {
        List<ClientSession> result = new ArrayList<>();
        for (ClientSession s : sessions.values()) {
            if (s.isActive() && s.getSubscribedQueues().contains(queueName)) {
                result.add(s);
            }
        }
        return result;
    }

    private void deliverToClient(ClientSession session, Message message,
                                 String destination, DestinationType destType, long offset) {
        int packetId = session.nextPacketId();
        session.trackDelivery(packetId,
                new PendingDelivery(message.id(), destination, destType, offset, Instant.now()));
        session.sendPublish(destination, message.payload(), packetId);
        log.debug("Delivered → {}: dest={}, pktId={}, msgId={}",
                session.getClientId(), destination, packetId, message.id());
    }

    /* ═══════════════ Periodic delivery loop ═══════════════ */

    private void deliveryLoop() {
        try {
            for (String topicName : topics.keySet()) {
                for (ClientSession s : sessions.values()) {
                    if (s.isActive() && s.getSubscribedTopics().contains(topicName)) {
                        deliverPendingTopicMessages(s, topicName);
                    }
                }
            }
            for (String queueName : queues.keySet()) {
                tryDeliverQueue(queueName);
            }
        } catch (Exception e) {
            log.error("Delivery loop error", e);
        }
    }
}
