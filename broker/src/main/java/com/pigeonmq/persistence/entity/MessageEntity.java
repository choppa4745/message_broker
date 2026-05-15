package com.pigeonmq.persistence.entity;

import com.pigeonmq.domain.DestinationType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Entity;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "messages")
public class MessageEntity {

    @Id
    @Column(name = "id", nullable = false)
    private UUID id;

    @Column(name = "destination_name", nullable = false, length = 255)
    private String destinationName;

    @Enumerated(EnumType.STRING)
    @Column(name = "dest_type", nullable = false, length = 16)
    private DestinationType destType;

    @Column(name = "payload", nullable = false)
    private byte[] payload;

    @Column(name = "priority", nullable = false)
    private int priority;

    @Column(name = "offset_val", nullable = false)
    private long offsetVal;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private MessageStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected MessageEntity() {}

    public MessageEntity(UUID id,
                         String destinationName,
                         DestinationType destType,
                         byte[] payload,
                         int priority,
                         long offsetVal,
                         MessageStatus status,
                         Instant createdAt) {
        this.id = id;
        this.destinationName = destinationName;
        this.destType = destType;
        this.payload = payload;
        this.priority = priority;
        this.offsetVal = offsetVal;
        this.status = status;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getDestinationName() {
        return destinationName;
    }

    public DestinationType getDestType() {
        return destType;
    }

    public byte[] getPayload() {
        return payload;
    }

    public int getPriority() {
        return priority;
    }

    public long getOffsetVal() {
        return offsetVal;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public void setStatus(MessageStatus status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

