package com.pigeonmq.domain;

import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.handler.codec.mqtt.MqttMessageBuilders;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttQoS;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class ClientSession {

    private static final int MAX_PACKET_ID = 65_535;

    private final String clientId;
    private final Channel channel;
    private final AtomicInteger packetIdSeq = new AtomicInteger(1);

    private final Map<Integer, PendingDelivery> pendingDeliveries = new ConcurrentHashMap<>();
    private final Set<String> subscribedTopics = ConcurrentHashMap.newKeySet();
    private final Set<String> subscribedQueues = ConcurrentHashMap.newKeySet();
    private final Map<String, Long> topicSentOffsets = new ConcurrentHashMap<>();

    public ClientSession(String clientId, Channel channel) {
        this.clientId = clientId;
        this.channel = channel;
    }

    public String getClientId()           { return clientId; }
    public Channel getChannel()           { return channel; }
    public boolean isActive()             { return channel != null && channel.isActive(); }
    public Set<String> getSubscribedTopics() { return subscribedTopics; }
    public Set<String> getSubscribedQueues() { return subscribedQueues; }

    public void subscribeTopic(String topicName) {
        subscribedTopics.add(topicName);
        topicSentOffsets.putIfAbsent(topicName, 0L);
    }

    public void restoreTopicSentOffsets(Map<String, Long> offsets) {
        if (offsets == null || offsets.isEmpty()) return;
        offsets.forEach((topic, offset) -> {
            if (topic == null || topic.isBlank()) return;
            if (offset == null) return;
            topicSentOffsets.merge(topic, offset, Math::max);
        });
    }

    public void subscribeQueue(String queueName) {
        subscribedQueues.add(queueName);
    }

    public void unsubscribeTopic(String topicName) {
        subscribedTopics.remove(topicName);
        topicSentOffsets.remove(topicName);
    }

    public void unsubscribeQueue(String queueName) {
        subscribedQueues.remove(queueName);
    }

    public long getTopicSentOffset(String topicName) {
        return topicSentOffsets.getOrDefault(topicName, 0L);
    }

    public void advanceTopicSentOffset(String topicName, long nextOffset) {
        topicSentOffsets.merge(topicName, nextOffset, Math::max);
    }

    public int nextPacketId() {
        return packetIdSeq.getAndUpdate(i -> (i % MAX_PACKET_ID) + 1);
    }

    public void trackDelivery(int packetId, PendingDelivery delivery) {
        pendingDeliveries.put(packetId, delivery);
    }

    public PendingDelivery completeDelivery(int packetId) {
        return pendingDeliveries.remove(packetId);
    }

    public void sendPublish(String topic, byte[] payload, int packetId) {
        MqttPublishMessage msg = MqttMessageBuilders.publish()
                .topicName(topic)
                .qos(MqttQoS.AT_LEAST_ONCE)
                .messageId(packetId)
                .payload(Unpooled.wrappedBuffer(payload))
                .build();
        channel.writeAndFlush(msg);
    }
}
