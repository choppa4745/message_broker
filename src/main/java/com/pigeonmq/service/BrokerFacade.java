package com.pigeonmq.service;

import com.pigeonmq.domain.*;
import io.netty.channel.Channel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BrokerFacade {

    private static final Logger log = LoggerFactory.getLogger(BrokerFacade.class);
    private static final String SHARED_PREFIX = "$share/";

    private final TopicService topicService;
    private final QueueService queueService;
    private final SessionService sessionService;
    private final DeliveryService deliveryService;

    public BrokerFacade(TopicService topicService,
                        QueueService queueService,
                        SessionService sessionService,
                        DeliveryService deliveryService) {
        this.topicService = topicService;
        this.queueService = queueService;
        this.sessionService = sessionService;
        this.deliveryService = deliveryService;
    }

    // ── Client lifecycle ───────────────────────────────────────────────

    public ClientSession registerClient(String clientId, Channel channel) {
        return sessionService.register(clientId, channel);
    }

    public void removeClient(Channel channel) {
        ClientSession session = sessionService.findByChannel(channel);
        if (session != null) {
            sessionService.remove(session.getClientId());
        }
    }

    public ClientSession findSession(Channel channel) {
        return sessionService.findByChannel(channel);
    }

    // ── Publish ────────────────────────────────────────────────────────

    public void publish(String destination, byte[] payload, int priority) {
        DestinationType type = resolveDestinationType(destination);

        if (type == DestinationType.QUEUE) {
            try {
                queueService.enqueue(destination, payload, priority);
                deliveryService.deliverQueueMessages(destination);
            } catch (Exception e) {
                log.warn("Failed to enqueue to {}: {}", destination, e.getMessage());
            }
        } else {
            topicService.publish(destination, payload, priority);
            deliveryService.deliverTopicMessages(destination);
        }
    }

    // ── Subscribe / Unsubscribe ────────────────────────────────────────

    public void subscribe(ClientSession session, String topicFilter) {
        ParsedSubscription sub = parseTopicFilter(topicFilter);

        if (sub.isShared()) {
            String queueName = sub.destination();
            if (!queueService.exists(queueName)) {
                queueService.create(queueName, QueueMode.FIFO);
            }
            session.subscribeQueue(queueName);
            log.info("Client {} subscribed to queue {}", session.getClientId(), queueName);
        } else {
            String topicName = sub.destination();
            if (!topicService.exists(topicName)) {
                topicService.create(topicName);
            }
            session.subscribeTopic(topicName);
            log.info("Client {} subscribed to topic {}", session.getClientId(), topicName);
        }
    }

    public void unsubscribe(ClientSession session, String topicFilter) {
        ParsedSubscription sub = parseTopicFilter(topicFilter);
        if (sub.isShared()) {
            session.unsubscribeQueue(sub.destination());
        } else {
            session.unsubscribeTopic(sub.destination());
        }
    }

    // ── ACK ────────────────────────────────────────────────────────────

    public void acknowledgeDelivery(ClientSession session, int packetId) {
        PendingDelivery delivery = session.completeDelivery(packetId);
        if (delivery == null) {
            log.debug("PUBACK for unknown packet {}", packetId);
            return;
        }
        if (delivery.destType() == DestinationType.QUEUE) {
            QueueStore queue = queueService.getOrThrow(delivery.destination());
            queue.ack(delivery.messageId());
            log.debug("ACK queue={} msgId={}", delivery.destination(), delivery.messageId());
        }
    }

    // ── Internal helpers ───────────────────────────────────────────────

    private DestinationType resolveDestinationType(String destination) {
        return queueService.exists(destination) ? DestinationType.QUEUE : DestinationType.TOPIC;
    }

    private ParsedSubscription parseTopicFilter(String filter) {
        if (filter.startsWith(SHARED_PREFIX)) {
            int secondSlash = filter.indexOf('/', SHARED_PREFIX.length());
            if (secondSlash > 0) {
                String destination = filter.substring(secondSlash + 1);
                return new ParsedSubscription(destination, true);
            }
        }
        return new ParsedSubscription(filter, false);
    }

    private record ParsedSubscription(String destination, boolean isShared) {}
}
