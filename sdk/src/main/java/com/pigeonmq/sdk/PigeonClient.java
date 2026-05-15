package com.pigeonmq.sdk;

import com.pigeonmq.sdk.exception.ConnectionException;
import com.pigeonmq.sdk.exception.PigeonException;
import com.pigeonmq.sdk.exception.PublishException;
import com.pigeonmq.sdk.exception.SubscriptionException;
import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttCallback;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.MqttDisconnectResponse;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;
import org.eclipse.paho.mqttv5.common.MqttSubscription;
import org.eclipse.paho.mqttv5.common.packet.MqttProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

/**
 * High-level pigeonMQ client.
 *
 * Wraps Eclipse Paho MQTT v5 async client and exposes a small, opinionated API:
 * <ul>
 *     <li>{@link #publishAsync(String, byte[], QoS)}</li>
 *     <li>{@link #subscribeTopic(String, QoS, MessageHandler)}</li>
 *     <li>{@link #subscribeQueue(String, String, QoS, MessageHandler)} (shared subscription)</li>
 *     <li>{@link #unsubscribe(String)}</li>
 * </ul>
 *
 * Lifecycle: {@link #connectAsync()} → use → {@link #disconnect()} / {@link #close()}.
 */
public final class PigeonClient implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(PigeonClient.class);
    private static final String SHARED_PREFIX = "$share/";

    public static PigeonClientBuilder builder() {
        return new PigeonClientBuilder();
    }

    private final PigeonClientBuilder.Config config;
    private final Executor callbackExecutor;
    private final boolean ownsCallbackExecutor;
    private final ConnectionListener connectionListener;
    private final MqttAsyncClient mqtt;

    private final ConcurrentMap<String, MessageHandler> handlers = new ConcurrentHashMap<>();
    private final AtomicReference<State> state = new AtomicReference<>(State.NEW);

    PigeonClient(PigeonClientBuilder.Config config) {
        this.config = config;
        if (config.callbackExecutor() != null) {
            this.callbackExecutor = config.callbackExecutor();
            this.ownsCallbackExecutor = false;
        } else {
            this.callbackExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "pigeon-sdk-callbacks");
                t.setDaemon(true);
                return t;
            });
            this.ownsCallbackExecutor = true;
        }
        this.connectionListener = config.connectionListener() != null
                ? config.connectionListener()
                : new ConnectionListener() {};
        try {
            this.mqtt = new MqttAsyncClient(config.serverUri(), config.clientId(), new MemoryPersistence());
        } catch (MqttException e) {
            throw new ConnectionException("Failed to create MQTT client", e);
        }
        this.mqtt.setCallback(new InternalCallback());
    }

    // ── Lifecycle ──────────────────────────────────────────────────────

    public CompletableFuture<Void> connectAsync() {
        if (!state.compareAndSet(State.NEW, State.CONNECTING)
                && !state.compareAndSet(State.DISCONNECTED, State.CONNECTING)) {
            return CompletableFuture.failedFuture(
                    new ConnectionException("Client cannot connect from state " + state.get()));
        }
        MqttConnectionOptions options = buildConnectionOptions();
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            mqtt.connect(options, null, tokenCallback(future, "connect"));
        } catch (MqttException e) {
            state.set(State.DISCONNECTED);
            future.completeExceptionally(new ConnectionException("Connect failed", e));
        }
        return future.thenApply(v -> {
            state.set(State.CONNECTED);
            log.info("Connected to {} as {}", config.serverUri(), config.clientId());
            return null;
        });
    }

    public void connect() {
        join(connectAsync(), ConnectionException::new);
    }

    public boolean isConnected() {
        return state.get() == State.CONNECTED && mqtt.isConnected();
    }

    public CompletableFuture<Void> disconnectAsync() {
        if (state.get() == State.CLOSED || state.get() == State.NEW) {
            return CompletableFuture.completedFuture(null);
        }
        state.set(State.DISCONNECTING);
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            mqtt.disconnect(null, tokenCallback(future, "disconnect"));
        } catch (MqttException e) {
            future.completeExceptionally(new ConnectionException("Disconnect failed", e));
        }
        return future.thenApply(v -> {
            state.set(State.DISCONNECTED);
            return null;
        });
    }

    public void disconnect() {
        join(disconnectAsync(), ConnectionException::new);
    }

    @Override
    public void close() {
        if (!state.compareAndSet(State.CONNECTED, State.CLOSED)
                && !state.compareAndSet(State.DISCONNECTED, State.CLOSED)
                && !state.compareAndSet(State.NEW, State.CLOSED)) {
            try {
                disconnect();
            } catch (Exception ignored) {
                // already in trouble, proceed to close anyway
            }
            state.set(State.CLOSED);
        }
        try {
            mqtt.close(true);
        } catch (MqttException e) {
            log.warn("Failed to close underlying MQTT client: {}", e.getMessage());
        }
        if (ownsCallbackExecutor && callbackExecutor instanceof java.util.concurrent.ExecutorService es) {
            es.shutdown();
        }
    }

    // ── Publish ────────────────────────────────────────────────────────

    public CompletableFuture<Void> publishAsync(String destination, byte[] payload, QoS qos) {
        ensureConnected();
        Objects.requireNonNull(destination, "destination");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(qos, "qos");
        MqttMessage message = new MqttMessage(payload);
        message.setQos(qos.level());
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            mqtt.publish(destination, message, null, tokenCallback(future, "publish"));
        } catch (MqttException e) {
            future.completeExceptionally(new PublishException(
                    "Failed to publish to " + destination, e));
        }
        return future;
    }

    public void publish(String destination, byte[] payload, QoS qos) {
        join(publishAsync(destination, payload, qos), PublishException::new);
    }

    // ── Subscribe ──────────────────────────────────────────────────────

    public CompletableFuture<Void> subscribeTopic(String topic, QoS qos, MessageHandler handler) {
        Objects.requireNonNull(topic, "topic");
        return subscribeInternal(topic, qos, handler);
    }

    public CompletableFuture<Void> subscribeQueue(String queue,
                                                   String group,
                                                   QoS qos,
                                                   MessageHandler handler) {
        Objects.requireNonNull(queue, "queue");
        Objects.requireNonNull(group, "group");
        String filter = SHARED_PREFIX + group + "/" + queue;
        return subscribeInternal(filter, qos, handler);
    }

    public CompletableFuture<Void> unsubscribe(String filter) {
        ensureConnected();
        Objects.requireNonNull(filter, "filter");
        CompletableFuture<Void> future = new CompletableFuture<>();
        try {
            mqtt.unsubscribe(filter, null, tokenCallback(future, "unsubscribe"));
        } catch (MqttException e) {
            future.completeExceptionally(new SubscriptionException(
                    "Failed to unsubscribe from " + filter, e));
        }
        return future.thenApply(v -> {
            handlers.remove(filter);
            return null;
        });
    }

    // ── Internals ──────────────────────────────────────────────────────

    private CompletableFuture<Void> subscribeInternal(String filter, QoS qos, MessageHandler handler) {
        ensureConnected();
        Objects.requireNonNull(qos, "qos");
        Objects.requireNonNull(handler, "handler");
        handlers.put(filter, handler);

        CompletableFuture<Void> future = new CompletableFuture<>();
        MqttSubscription subscription = new MqttSubscription(filter, qos.level());
        try {
            mqtt.subscribe(
                    new MqttSubscription[]{subscription},
                    null,
                    tokenCallback(future, "subscribe"),
                    new org.eclipse.paho.mqttv5.client.IMqttMessageListener[]{
                            (topic, mqttMessage) -> dispatchMessage(topic, mqttMessage, handler)
                    },
                    new MqttProperties()
            );
        } catch (MqttException e) {
            handlers.remove(filter);
            future.completeExceptionally(new SubscriptionException(
                    "Failed to subscribe to " + filter, e));
        }
        return future;
    }

    private void dispatchMessage(String topic, MqttMessage mqttMessage, MessageHandler handler) {
        PigeonMessage message = new PigeonMessage(
                topic,
                mqttMessage.getPayload(),
                QoS.fromLevel(mqttMessage.getQos()),
                mqttMessage.isRetained());
        callbackExecutor.execute(() -> {
            try {
                handler.onMessage(message);
            } catch (Exception e) {
                log.error("MessageHandler threw for {}: {}", topic, e.getMessage(), e);
            }
        });
    }

    private MqttConnectionOptions buildConnectionOptions() {
        MqttConnectionOptions options = new MqttConnectionOptions();
        options.setCleanStart(config.cleanStart());
        options.setAutomaticReconnect(config.automaticReconnect());
        options.setKeepAliveInterval((int) config.keepAlive().toSeconds());
        options.setConnectionTimeout((int) config.connectTimeout().toSeconds());
        options.setAutomaticReconnectDelay(
                (int) config.reconnectMinDelay().toSeconds(),
                (int) config.reconnectMaxDelay().toSeconds());
        if (config.username() != null) {
            options.setUserName(config.username());
        }
        if (config.password() != null) {
            byte[] bytes = new String(config.password()).getBytes();
            options.setPassword(bytes);
        }
        return options;
    }

    private void ensureConnected() {
        if (!isConnected()) {
            throw new ConnectionException("Client is not connected (state=" + state.get() + ")");
        }
    }

    private static <T extends RuntimeException> void join(CompletableFuture<Void> future,
                                                           java.util.function.BiFunction<String, Throwable, T> ex) {
        try {
            future.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw ex.apply("Interrupted while waiting for MQTT operation", e);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            if (cause instanceof PigeonException pe) {
                throw pe;
            }
            throw ex.apply("MQTT operation failed: " + cause.getMessage(), cause);
        }
    }

    private static org.eclipse.paho.mqttv5.client.MqttActionListener tokenCallback(
            CompletableFuture<Void> future, String action) {
        return new org.eclipse.paho.mqttv5.client.MqttActionListener() {
            @Override
            public void onSuccess(IMqttToken token) {
                future.complete(null);
            }

            @Override
            public void onFailure(IMqttToken token, Throwable exception) {
                future.completeExceptionally(new PigeonException("MQTT " + action + " failed", exception));
            }
        };
    }

    // ── Paho callback bridge ───────────────────────────────────────────

    private final class InternalCallback implements MqttCallback {

        @Override
        public void disconnected(MqttDisconnectResponse disconnectResponse) {
            Throwable cause = disconnectResponse.getException();
            if (cause == null) {
                log.info("Disconnected from broker");
            } else {
                log.warn("Connection lost: {}", cause.getMessage());
            }
            callbackExecutor.execute(() -> connectionListener.onConnectionLost(cause));
        }

        @Override
        public void mqttErrorOccurred(MqttException exception) {
            log.warn("MQTT error: {}", exception.getMessage());
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            // Per-subscription listeners are wired in subscribeInternal; this fallback
            // routes anything that arrives without a listener (shouldn't normally happen).
            MessageHandler handler = handlers.get(topic);
            if (handler != null) {
                dispatchMessage(topic, message, handler);
            } else {
                log.debug("Message arrived for {} but no handler registered", topic);
            }
        }

        @Override
        public void deliveryComplete(IMqttToken token) {
            // no-op: completion is signalled via CompletableFuture in publishAsync
        }

        @Override
        public void connectComplete(boolean reconnect, String serverURI) {
            if (reconnect) {
                log.info("Reconnected to {}", serverURI);
                callbackExecutor.execute(() -> connectionListener.onReconnected(serverURI));
            } else {
                callbackExecutor.execute(() -> connectionListener.onConnected(serverURI));
            }
        }

        @Override
        public void authPacketArrived(int reasonCode, MqttProperties properties) {
            // unused (no enhanced auth)
        }
    }

    private enum State {
        NEW, CONNECTING, CONNECTED, DISCONNECTING, DISCONNECTED, CLOSED
    }
}
