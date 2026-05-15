package com.pigeonmq.sdk;

import java.nio.charset.StandardCharsets;
import java.util.Objects;

/**
 * Immutable message delivered to a subscriber.
 */
public final class PigeonMessage {

    private final String destination;
    private final byte[] payload;
    private final QoS qos;
    private final boolean retained;

    public PigeonMessage(String destination, byte[] payload, QoS qos, boolean retained) {
        this.destination = Objects.requireNonNull(destination, "destination");
        this.payload = Objects.requireNonNull(payload, "payload").clone();
        this.qos = Objects.requireNonNull(qos, "qos");
        this.retained = retained;
    }

    public String destination() {
        return destination;
    }

    public byte[] payload() {
        return payload.clone();
    }

    public String payloadAsString() {
        return new String(payload, StandardCharsets.UTF_8);
    }

    public QoS qos() {
        return qos;
    }

    public boolean retained() {
        return retained;
    }
}
