package com.pigeonmq.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "consumer_offsets")
public class ConsumerOffsetEntity {

    @EmbeddedId
    private ConsumerOffsetId id;

    @Column(name = "sent_offset", nullable = false)
    private long sentOffset;

    protected ConsumerOffsetEntity() {}

    public ConsumerOffsetEntity(ConsumerOffsetId id, long sentOffset) {
        this.id = id;
        this.sentOffset = sentOffset;
    }

    public ConsumerOffsetId getId() {
        return id;
    }

    public long getSentOffset() {
        return sentOffset;
    }

    public void setSentOffset(long sentOffset) {
        this.sentOffset = sentOffset;
    }
}

