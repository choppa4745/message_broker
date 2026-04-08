package com.pigeonmq.config;

public class BrokerConfig {

    private int mqttPort = 1883;
    private int httpPort = 8080;
    private int deliveryIntervalMs = 100;
    private int ackTimeoutSeconds = 30;
    private int maxRetries = 5;
    private int workerPoolSize = 4;

    public static BrokerConfig fromEnv() {
        BrokerConfig cfg = new BrokerConfig();
        cfg.mqttPort = intEnv("PIGEONMQ_MQTT_PORT", 1883);
        cfg.httpPort = intEnv("PIGEONMQ_HTTP_PORT", 8080);
        cfg.deliveryIntervalMs = intEnv("PIGEONMQ_DELIVERY_INTERVAL_MS", 100);
        cfg.ackTimeoutSeconds = intEnv("PIGEONMQ_ACK_TIMEOUT_SECONDS", 30);
        cfg.maxRetries = intEnv("PIGEONMQ_MAX_RETRIES", 5);
        cfg.workerPoolSize = intEnv("PIGEONMQ_WORKER_POOL_SIZE", 4);
        return cfg;
    }

    private static int intEnv(String name, int defaultValue) {
        String val = System.getenv(name);
        if (val == null || val.isBlank()) return defaultValue;
        return Integer.parseInt(val);
    }

    public int getMqttPort()          { return mqttPort; }
    public int getHttpPort()          { return httpPort; }
    public int getDeliveryIntervalMs(){ return deliveryIntervalMs; }
    public int getAckTimeoutSeconds() { return ackTimeoutSeconds; }
    public int getMaxRetries()        { return maxRetries; }
    public int getWorkerPoolSize()    { return workerPoolSize; }

    @Override
    public String toString() {
        return "BrokerConfig{mqttPort=" + mqttPort
                + ", httpPort=" + httpPort
                + ", deliveryIntervalMs=" + deliveryIntervalMs
                + ", ackTimeoutSeconds=" + ackTimeoutSeconds
                + ", maxRetries=" + maxRetries
                + ", workerPoolSize=" + workerPoolSize + '}';
    }
}
