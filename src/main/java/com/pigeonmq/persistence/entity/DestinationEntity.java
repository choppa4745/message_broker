package com.pigeonmq.persistence.entity;

import com.pigeonmq.domain.DestinationType;
import com.pigeonmq.domain.QueueMode;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Entity;

import java.time.Instant;

@Entity
@Table(name = "destinations")
public class DestinationEntity {

    @Id
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 16)
    private DestinationType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "mode", length = 16)
    private QueueMode mode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected DestinationEntity() {}

    public DestinationEntity(String name, DestinationType type, QueueMode mode, Instant createdAt) {
        this.name = name;
        this.type = type;
        this.mode = mode;
        this.createdAt = createdAt;
    }

    public String getName() {
        return name;
    }

    public DestinationType getType() {
        return type;
    }

    public QueueMode getMode() {
        return mode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}

