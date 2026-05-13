package com.benchmark;

import com.rabbitmq.client.ConnectionFactory;
import redis.clients.jedis.Jedis;

import java.nio.file.Path;
import java.util.*;

public class BenchmarkApp {

    static final String QUEUE = "benchmark_queue";

    private static final int[] DEFAULT_SIZES = {128, 1024, 10_240, 102_400};
    private static final int[] DEFAULT_RATES = {1_000, 5_000, 10_000, 20_000, 50_000};

    private static final int[] QUICK_SIZES = {128, 1024, 10_240};
    private static final int[] QUICK_RATES = {1_000, 5_000, 10_000};

    public static void main(String[] args) throws Exception {
        int duration = 30;
        boolean quick = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--duration" -> duration = Integer.parseInt(args[++i]);
                case "--quick"    -> { quick = true; duration = Math.min(duration, 10); }
            }
        }

        int[] sizes = quick ? QUICK_SIZES : DEFAULT_SIZES;
        int[] rates = quick ? QUICK_RATES : DEFAULT_RATES;

        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║   RabbitMQ vs Redis — Benchmark Suite               ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.printf("  Duration per test : %d s%n", duration);
        System.out.printf("  Message sizes     : %s%n", Arrays.toString(sizes));
        System.out.printf("  Target rates      : %s%n", Arrays.toString(rates));
        System.out.println();

        checkBrokers();

        Map<String, List<ExperimentResult>> results = runSuite(duration, sizes, rates);

        Path outDir = Path.of("results");
        new ReportGenerator(results, outDir).generate();

        System.out.println();
        System.out.println("Report  → results/report.md");
        System.out.println("JSON    → results/raw_results.json");
    }

    // ── connectivity check ─────────────────────────────────

    private static void checkBrokers() {
        System.out.print("Checking RabbitMQ … ");
        try {
            ConnectionFactory f = new ConnectionFactory();
            f.setHost("localhost");
            f.setPort(5672);
            f.newConnection().close();
            System.out.println("OK");
        } catch (Exception e) {
            System.out.println("FAIL: " + e.getMessage());
            System.exit(1);
        }

        System.out.print("Checking Redis … ");
        try (Jedis j = new Jedis("localhost", 6379)) {
            j.ping();
            System.out.println("OK");
        } catch (Exception e) {
            System.out.println("FAIL: " + e.getMessage());
            System.exit(1);
        }
        System.out.println();
    }

    // ── suite orchestration ────────────────────────────────

    private static Map<String, List<ExperimentResult>> runSuite(
            int duration, int[] sizes, int[] rates) {

        ExperimentRunner runner = new ExperimentRunner();
        Map<String, List<ExperimentResult>> all = new LinkedHashMap<>();
        all.put("base",   new ArrayList<>());
        all.put("size",   new ArrayList<>());
        all.put("stress", new ArrayList<>());

        header("EXPERIMENT 1: BASE COMPARISON  (1 KB, 1 000 msg/s)");
        for (String broker : new String[]{"rabbitmq", "redis"}) {
            ExperimentConfig cfg = new ExperimentConfig(
                    broker, 1024, 1_000, duration, "base");
            logStart(cfg);
            ExperimentResult r = runner.run(cfg);
            logEnd(r);
            all.get("base").add(r);
        }

        header("EXPERIMENT 2: MESSAGE SIZE IMPACT");
        for (int sz : sizes) {
            for (String broker : new String[]{"rabbitmq", "redis"}) {
                ExperimentConfig cfg = new ExperimentConfig(
                        broker, sz, 1_000, duration, "size");
                logStart(cfg);
                ExperimentResult r = runner.run(cfg);
                logEnd(r);
                all.get("size").add(r);
            }
        }

        header("EXPERIMENT 3: THROUGHPUT STRESS TEST");
        for (int rate : rates) {
            for (String broker : new String[]{"rabbitmq", "redis"}) {
                ExperimentConfig cfg = new ExperimentConfig(
                        broker, 1024, rate, duration, "stress");
                logStart(cfg);
                ExperimentResult r = runner.run(cfg);
                logEnd(r);
                all.get("stress").add(r);
            }
        }

        return all;
    }

    // ── logging helpers ────────────────────────────────────

    private static void header(String title) {
        System.out.println();
        System.out.println("═".repeat(60));
        System.out.println(title);
        System.out.println("═".repeat(60));
    }

    private static void logStart(ExperimentConfig cfg) {
        System.out.printf("%n▶ %s%n", cfg);
    }

    private static void logEnd(ExperimentResult r) {
        System.out.printf("  sent=%,d | recv=%,d | lost=%,d | " +
                        "avg_lat=%.2f ms | p95_lat=%.2f ms%n",
                r.getMessagesSent(), r.getMessagesReceived(), r.getMessagesLost(),
                r.getAvgLatencyMs(), r.getP95LatencyMs());
    }
}
