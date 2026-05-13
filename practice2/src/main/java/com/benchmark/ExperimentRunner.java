package com.benchmark;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import redis.clients.jedis.Jedis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ExperimentRunner {

    private static final int DRAIN_TIMEOUT_SEC = 10;

    public ExperimentResult run(ExperimentConfig cfg) {
        cleanup(cfg.getBroker());
        sleep(1000);

        AtomicBoolean stopConsumers = new AtomicBoolean(false);
        AtomicBoolean stopProducers = new AtomicBoolean(false);

        ConsumerWorker consumer = new ConsumerWorker(cfg.getBroker(), stopConsumers);
        Thread consumerThread = new Thread(consumer, "consumer-0");
        consumerThread.setDaemon(true);
        consumerThread.start();
        sleep(500);

        ProducerWorker producer = new ProducerWorker(
                cfg.getBroker(), cfg.getMsgSize(), cfg.getTargetRate(), stopProducers);
        Thread producerThread = new Thread(producer, "producer-0");
        producerThread.setDaemon(true);
        producerThread.start();

        sleep(cfg.getDuration() * 1000L);

        stopProducers.set(true);
        join(producerThread, 10_000);

        sleep(DRAIN_TIMEOUT_SEC * 1000L);
        stopConsumers.set(true);
        join(consumerThread, 10_000);

        return aggregate(cfg, producer, consumer);
    }

    private ExperimentResult aggregate(ExperimentConfig cfg,
                                       ProducerWorker producer,
                                       ConsumerWorker consumer) {
        ExperimentResult r = new ExperimentResult(cfg);
        r.setMessagesSent(producer.getSentCount());
        r.setMessagesReceived(consumer.getReceivedCount());
        r.setSendErrors(producer.getErrorCount());
        r.setRecvErrors(consumer.getErrorCount());
        r.setActualSendRate((double) producer.getSentCount() / cfg.getDuration());
        r.setActualRecvRate((double) consumer.getReceivedCount() / cfg.getDuration());

        List<Long> latencies = consumer.drainLatencies();
        if (!latencies.isEmpty()) {
            Collections.sort(latencies);
            int n = latencies.size();
            double sum = 0;
            for (long l : latencies) sum += l;
            r.setAvgLatencyMs(sum / n / 1_000_000.0);
            r.setMinLatencyMs(latencies.get(0) / 1_000_000.0);
            r.setP50LatencyMs(latencies.get(n / 2) / 1_000_000.0);
            r.setP95LatencyMs(latencies.get((int) (n * 0.95)) / 1_000_000.0);
            r.setP99LatencyMs(latencies.get(Math.min((int) (n * 0.99), n - 1)) / 1_000_000.0);
            r.setMaxLatencyMs(latencies.get(n - 1) / 1_000_000.0);
        }

        cleanup(cfg.getBroker());
        return r;
    }


    private void cleanup(String broker) {
        if ("rabbitmq".equals(broker)) {
            cleanupRabbitMQ();
        } else {
            cleanupRedis();
        }
    }

    private void cleanupRabbitMQ() {
        try {
            ConnectionFactory f = new ConnectionFactory();
            f.setHost("localhost");
            f.setPort(5672);
            try (Connection c = f.newConnection(); Channel ch = c.createChannel()) {
                ch.queueDelete(BenchmarkApp.QUEUE);
            }
        } catch (Exception ignored) {}
    }

    private void cleanupRedis() {
        try (Jedis j = new Jedis("localhost", 6379)) {
            j.del(BenchmarkApp.QUEUE);
        } catch (Exception ignored) {}
    }


    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void join(Thread t, long ms) {
        try { t.join(ms); } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
