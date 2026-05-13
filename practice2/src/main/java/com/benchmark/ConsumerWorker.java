package com.benchmark;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import redis.clients.jedis.Jedis;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ConsumerWorker implements Runnable {

    private final String broker;
    private final AtomicBoolean stopFlag;

    private final AtomicLong receivedCount = new AtomicLong();
    private final AtomicLong errorCount    = new AtomicLong();
    private final ConcurrentLinkedQueue<Long> latenciesNano = new ConcurrentLinkedQueue<>();

    public ConsumerWorker(String broker, AtomicBoolean stopFlag) {
        this.broker = broker;
        this.stopFlag = stopFlag;
    }

    public long getReceivedCount() { return receivedCount.get(); }
    public long getErrorCount()    { return errorCount.get(); }

    public List<Long> drainLatencies() {
        return new ArrayList<>(latenciesNano);
    }

    @Override
    public void run() {
        try {
            if ("rabbitmq".equals(broker)) {
                runRabbitMQ();
            } else {
                runRedis();
            }
        } catch (Exception e) {
            System.err.println("Consumer fatal: " + e.getMessage());
        }
    }

    private void processBody(byte[] body) {
        long recvTime = System.nanoTime();
        if (body.length >= 8) {
            long sendTime = ByteBuffer.wrap(body, 0, 8).getLong();
            latenciesNano.add(recvTime - sendTime);
        }
        receivedCount.incrementAndGet();
    }


    private void runRabbitMQ() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setPort(5672);
        factory.setRequestedHeartbeat(120);

        Connection conn = factory.newConnection();
        Channel channel = conn.createChannel();
        channel.queueDeclare(BenchmarkApp.QUEUE, false, false, false, null);
        channel.basicQos(500);

        DeliverCallback deliver = (tag, delivery) -> {
            try {
                processBody(delivery.getBody());
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            } catch (Exception e) {
                errorCount.incrementAndGet();
            }
        };

        String consumerTag = channel.basicConsume(
                BenchmarkApp.QUEUE, false, deliver, t -> {});

        while (!stopFlag.get()) {
            try { Thread.sleep(100); } catch (InterruptedException e) { break; }
        }

        try { channel.basicCancel(consumerTag); } catch (Exception ignored) {}
        try { channel.close(); } catch (Exception ignored) {}
        try { conn.close(); } catch (Exception ignored) {}
    }


    private void runRedis() {
        try (Jedis jedis = new Jedis("localhost", 6379, 5000)) {
            jedis.ping();
            byte[] key = BenchmarkApp.QUEUE.getBytes();

            while (!stopFlag.get()) {
                try {
                    List<byte[]> result = jedis.brpop(1, key);
                    if (result != null && result.size() == 2) {
                        processBody(result.get(1));
                    }
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
            }
        }
    }
}
