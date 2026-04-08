package com.pigeonmq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pigeonmq")
public class BrokerProperties {

    private int mqttPort = 1883;
    private int deliveryIntervalMs = 100;
    private int ackTimeoutSeconds = 30;
    private int maxRetries = 5;
    private int workerPoolSize = 4;

    public int getMqttPort()           { return mqttPort; }
    public int getDeliveryIntervalMs() { return deliveryIntervalMs; }
    public int getAckTimeoutSeconds()  { return ackTimeoutSeconds; }
    public int getMaxRetries()         { return maxRetries; }
    public int getWorkerPoolSize()     { return workerPoolSize; }

    public void setMqttPort(int mqttPort)                     { this.mqttPort = mqttPort; }
    public void setDeliveryIntervalMs(int deliveryIntervalMs) { this.deliveryIntervalMs = deliveryIntervalMs; }
    public void setAckTimeoutSeconds(int ackTimeoutSeconds)   { this.ackTimeoutSeconds = ackTimeoutSeconds; }
    public void setMaxRetries(int maxRetries)                 { this.maxRetries = maxRetries; }
    public void setWorkerPoolSize(int workerPoolSize)         { this.workerPoolSize = workerPoolSize; }
}
