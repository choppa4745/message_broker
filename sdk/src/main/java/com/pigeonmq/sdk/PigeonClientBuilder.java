package com.pigeonmq.sdk;

import com.pigeonmq.sdk.exception.PigeonException;

import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Executor;

/**
 * Builder for {@link PigeonClient}. Sensible defaults are provided for every option,
 * only host/port are typically customised in tests.
 */
public final class PigeonClientBuilder {

    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 1883;
    private static final Duration DEFAULT_KEEP_ALIVE = Duration.ofSeconds(30);
    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration DEFAULT_RECONNECT_MIN_DELAY = Duration.ofSeconds(1);
    private static final Duration DEFAULT_RECONNECT_MAX_DELAY = Duration.ofMinutes(2);

    private String host = DEFAULT_HOST;
    private int port = DEFAULT_PORT;
    private String clientId;
    private String username;
    private char[] password;
    private boolean cleanStart = true;
    private boolean automaticReconnect = true;
    private Duration keepAlive = DEFAULT_KEEP_ALIVE;
    private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    private Duration reconnectMinDelay = DEFAULT_RECONNECT_MIN_DELAY;
    private Duration reconnectMaxDelay = DEFAULT_RECONNECT_MAX_DELAY;
    private Executor callbackExecutor;
    private ConnectionListener connectionListener;

    PigeonClientBuilder() {}

    public PigeonClientBuilder host(String host) {
        this.host = Objects.requireNonNull(host, "host");
        return this;
    }

    public PigeonClientBuilder port(int port) {
        if (port <= 0 || port > 65_535) {
            throw new IllegalArgumentException("port must be in 1..65535");
        }
        this.port = port;
        return this;
    }

    public PigeonClientBuilder clientId(String clientId) {
        this.clientId = Objects.requireNonNull(clientId, "clientId");
        return this;
    }

    public PigeonClientBuilder credentials(String username, String password) {
        this.username = username;
        this.password = password == null ? null : password.toCharArray();
        return this;
    }

    public PigeonClientBuilder cleanStart(boolean cleanStart) {
        this.cleanStart = cleanStart;
        return this;
    }

    public PigeonClientBuilder automaticReconnect(boolean enabled) {
        this.automaticReconnect = enabled;
        return this;
    }

    public PigeonClientBuilder keepAlive(Duration keepAlive) {
        this.keepAlive = Objects.requireNonNull(keepAlive, "keepAlive");
        return this;
    }

    public PigeonClientBuilder connectTimeout(Duration connectTimeout) {
        this.connectTimeout = Objects.requireNonNull(connectTimeout, "connectTimeout");
        return this;
    }

    public PigeonClientBuilder reconnectDelay(Duration min, Duration max) {
        this.reconnectMinDelay = Objects.requireNonNull(min, "min");
        this.reconnectMaxDelay = Objects.requireNonNull(max, "max");
        return this;
    }

    /**
     * Executor used to invoke {@link MessageHandler} and {@link ConnectionListener} callbacks.
     * If omitted, a single-thread executor is provisioned internally.
     */
    public PigeonClientBuilder callbackExecutor(Executor executor) {
        this.callbackExecutor = Objects.requireNonNull(executor, "executor");
        return this;
    }

    public PigeonClientBuilder connectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
        return this;
    }

    public PigeonClient build() {
        if (host == null || host.isBlank()) {
            throw new PigeonException("host must not be blank");
        }
        if (clientId == null || clientId.isBlank()) {
            clientId = "pigeon-sdk-" + UUID.randomUUID();
        }
        return new PigeonClient(new Config(
                host, port, clientId, username, password,
                cleanStart, automaticReconnect, keepAlive, connectTimeout,
                reconnectMinDelay, reconnectMaxDelay,
                callbackExecutor, connectionListener));
    }

    /**
     * Immutable bundle of resolved configuration values.
     */
    record Config(
            String host,
            int port,
            String clientId,
            String username,
            char[] password,
            boolean cleanStart,
            boolean automaticReconnect,
            Duration keepAlive,
            Duration connectTimeout,
            Duration reconnectMinDelay,
            Duration reconnectMaxDelay,
            Executor callbackExecutor,
            ConnectionListener connectionListener
    ) {
        String serverUri() {
            return "tcp://" + host + ":" + port;
        }
    }
}
