package com.benchmark;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import redis.clients.jedis.Jedis;

import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ProducerWorker implements Runnable {

    private final String broker;
    private final int msgSize;
    private final int targetRate;
    private final AtomicBoolean stopFlag;

    private final AtomicLong sentCount  = new AtomicLong();
    private final AtomicLong errorCount = new AtomicLong();

    private final byte[] payload;

    public ProducerWorker(String broker, int msgSize, int targetRate,
                          AtomicBoolean stopFlag) {
        this.broker = broker;
        this.msgSize = msgSize;
        this.targetRate = targetRate;
        this.stopFlag = stopFlag;
        this.payload = new byte[Math.max(0, msgSize - 8)];
    }

    public long getSentCount()  { return sentCount.get(); }
    public long getErrorCount() { return errorCount.get(); }

    @Override
    public void run() {
        try {
            if ("rabbitmq".equals(broker)) {
                runRabbitMQ();
            } else {
                runRedis();
            }
        } catch (Exception e) {
            System.err.println("Producer fatal: " + e.getMessage());
        }
    }

    private void runRabbitMQ() throws Exception {
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost("localhost");
        factory.setPort(5672);
        factory.setRequestedHeartbeat(120);

        try (Connection conn = factory.newConnection();
             Channel channel = conn.createChannel()) {

            channel.queueDeclare(BenchmarkApp.QUEUE, false, false, false, null);

            long startNanos = System.nanoTime();
            while (!stopFlag.get()) {
                byte[] msg = buildMessage();
                try {
                    channel.basicPublish("", BenchmarkApp.QUEUE, null, msg);
                    sentCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
                rateLimit(startNanos);
            }
        }
    }

    private void runRedis() {
        try (Jedis jedis = new Jedis("localhost", 6379, 5000)) {
            jedis.ping();
            byte[] key = BenchmarkApp.QUEUE.getBytes();

            long startNanos = System.nanoTime();
            while (!stopFlag.get()) {
                byte[] msg = buildMessage();
                try {
                    jedis.lpush(key, msg);
                    sentCount.incrementAndGet();
                } catch (Exception e) {
                    errorCount.incrementAndGet();
                }
                rateLimit(startNanos);
            }
        }
    }

    private byte[] buildMessage() {
        ByteBuffer buf = ByteBuffer.allocate(msgSize);
        buf.putLong(System.nanoTime());
        if (payload.length > 0) {
            buf.put(payload);
        }
        return buf.array();
    }

    private void rateLimit(long startNanos) {
        if (targetRate <= 0) return;
        long sent = sentCount.get();
        long expectedNano = startNanos + (long) (sent * 1_000_000_000.0 / targetRate);
        long delta = expectedNano - System.nanoTime();
        if (delta > 1_500_000) {
            try {
                Thread.sleep(delta / 1_000_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else if (delta > 0) {
            while (System.nanoTime() < expectedNano) {
                Thread.onSpinWait();
            }
        }
    }
}
