package com.pigeonmq.sdk;

/**
 * Notifications about MQTT connection state changes.
 * Default methods are no-ops, override only what you care about.
 */
public interface ConnectionListener {

    default void onConnected(String serverUri) {}

    default void onReconnected(String serverUri) {}

    default void onConnectionLost(Throwable cause) {}
}
