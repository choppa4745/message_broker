package com.pigeonmq.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ConsumerOffsetId implements Serializable {

    @Column(name = "client_id", nullable = false, length = 255)
    private String clientId;

    @Column(name = "topic_name", nullable = false, length = 255)
    private String topicName;

    protected ConsumerOffsetId() {}

    public ConsumerOffsetId(String clientId, String topicName) {
        this.clientId = clientId;
        this.topicName = topicName;
    }

    public String getClientId() {
        return clientId;
    }

    public String getTopicName() {
        return topicName;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConsumerOffsetId that = (ConsumerOffsetId) o;
        return Objects.equals(clientId, that.clientId) && Objects.equals(topicName, that.topicName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientId, topicName);
    }
}

