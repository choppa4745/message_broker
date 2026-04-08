package com.pigeonmq;

import com.pigeonmq.config.BrokerConfig;
import com.pigeonmq.core.Broker;
import com.pigeonmq.transport.http.HttpApiServer;
import com.pigeonmq.transport.mqtt.MqttTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class PigeonMQServer {

    private static final Logger log = LoggerFactory.getLogger(PigeonMQServer.class);

    public static void main(String[] args) throws Exception {
        log.info("=== pigeonMQ starting ===");

        BrokerConfig config = BrokerConfig.fromEnv();
        log.info("Config: {}", config);

        Broker broker = new Broker(config);
        MqttTransport mqtt = new MqttTransport(config, broker);
        HttpApiServer http = new HttpApiServer(config, broker);

        broker.start();
        mqtt.start();
        http.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("=== pigeonMQ shutting down ===");
            http.stop();
            mqtt.stop();
            broker.stop();
        }));

        log.info("=== pigeonMQ ready ===");
        log.info("  MQTT : localhost:{}", config.getMqttPort());
        log.info("  HTTP : localhost:{}", config.getHttpPort());

        mqtt.awaitTermination();
    }
}
