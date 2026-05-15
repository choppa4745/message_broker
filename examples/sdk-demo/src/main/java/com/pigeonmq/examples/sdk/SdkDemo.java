package com.pigeonmq.examples.sdk;

import com.pigeonmq.sdk.ConnectionListener;
import com.pigeonmq.sdk.MessageHandler;
import com.pigeonmq.sdk.PigeonClient;
import com.pigeonmq.sdk.QoS;
import com.pigeonmq.sdk.admin.PigeonAdminClient;
import com.pigeonmq.sdk.admin.QueueMode;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Демонстрация SDK: REST admin + MQTT топик + MQTT очередь (shared subscription).
 * <p>
 * Перед запуском подними брокер, например: {@code docker compose up -d --build}
 */
public final class SdkDemo {

    public static void main(String[] args) throws Exception {
        String host = arg(args, 0, "localhost");
        int mqttPort = Integer.parseInt(arg(args, 1, "1883"));
        String httpBase = arg(args, 2, "http://localhost:8080");

        System.out.println("=== Admin API (SDK) ===");
        PigeonAdminClient admin = PigeonAdminClient.builder().baseUrl(httpBase).build();
        var health = admin.health();
        System.out.println("health: " + health.status()
                + " topics=" + health.topicCount()
                + " queues=" + health.queueCount());

        String topicName = "sdk-demo-topic";
        String queueName = "sdk-demo-queue";
        try {
            admin.deleteTopic(topicName);
        } catch (Exception ignored) {
        }
        try {
            admin.deleteQueue(queueName);
        } catch (Exception ignored) {
        }

        admin.createTopic(topicName);
        admin.createQueue(queueName, QueueMode.FIFO);
        System.out.println("созданы topic=" + topicName + " queue=" + queueName);

        String clientBase = "sdk-demo-" + System.currentTimeMillis();

        System.out.println("\n=== MQTT: топик (fan-out) ===");
        CountDownLatch topicLatch = new CountDownLatch(1);
        PigeonClient subTopic = mqttClient(host, mqttPort, clientBase + "-topic-sub");
        subTopic.connect();
        subTopic.subscribeTopic(topicName, QoS.AT_LEAST_ONCE, msg -> {
            System.out.println("[topic] " + msg.destination() + " -> " + msg.payloadAsString());
            topicLatch.countDown();
        });

        PigeonClient pub = mqttClient(host, mqttPort, clientBase + "-pub");
        pub.connect();
        pub.publish(topicName, "hello-topic".getBytes(), QoS.AT_LEAST_ONCE);
        System.out.println("опубликовано в топик");

        if (!topicLatch.await(15, TimeUnit.SECONDS)) {
            System.err.println("таймаут: сообщение топика не получено за 15 с");
        }

        System.out.println("\n=== MQTT: очередь (competing consumers) ===");
        PigeonClient q1 = mqttClient(host, mqttPort, clientBase + "-q1");
        PigeonClient q2 = mqttClient(host, mqttPort, clientBase + "-q2");
        q1.connect();
        q2.connect();
        CountDownLatch queueLatch = new CountDownLatch(2);
        MessageHandler qh = msg -> {
            System.out.println("[queue] " + msg.payloadAsString());
            queueLatch.countDown();
        };
        q1.subscribeQueue(queueName, "workers", QoS.AT_LEAST_ONCE, qh);
        q2.subscribeQueue(queueName, "workers", QoS.AT_LEAST_ONCE, qh);
        Thread.sleep(500);

        pub.publish(queueName, "q-msg-1".getBytes(), QoS.AT_LEAST_ONCE);
        pub.publish(queueName, "q-msg-2".getBytes(), QoS.AT_LEAST_ONCE);
        if (!queueLatch.await(15, TimeUnit.SECONDS)) {
            System.err.println("таймаут: не все сообщения очереди получены за 15 с");
        }

        pub.disconnect();
        q1.close();
        q2.close();
        subTopic.close();

        System.out.println("\n=== Готово ===");
    }

    private static String arg(String[] args, int i, String def) {
        return args.length > i ? args[i] : def;
    }

    private static PigeonClient mqttClient(String host, int port, String clientId) {
        return PigeonClient.builder()
                .host(host)
                .port(port)
                .clientId(clientId)
                .connectionListener(new ConnectionListener() {
                    @Override
                    public void onConnected(String serverUri) {
                        System.out.println("MQTT подключено: " + serverUri + " (" + clientId + ")");
                    }

                    @Override
                    public void onReconnected(String serverUri) {
                        System.out.println("MQTT переподключено: " + serverUri);
                    }

                    @Override
                    public void onConnectionLost(Throwable cause) {
                        System.err.println("MQTT обрыв: "
                                + (cause != null ? cause.getMessage() : "unknown"));
                    }
                })
                .build();
    }
}
