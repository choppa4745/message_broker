package com.practice3.loadgen;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

public class ScenarioRunner {

    private final Config cfg;
    private final ApiClient api;
    private final Scenario scenario;
    private final long seed;

    public ScenarioRunner(Config cfg, ApiClient api, Scenario scenario, long seed) {
        this.cfg = cfg;
        this.api = api;
        this.scenario = scenario;
        this.seed = seed;
    }

    public void runWarmup() throws Exception {
        run(cfg.warmupSeconds, false);
    }

    public RunStats runMeasured() throws Exception {
        return run(cfg.durationSeconds, true);
    }

    private RunStats run(int seconds, boolean collectLatency) throws Exception {
        long endAt = System.nanoTime() + seconds * 1_000_000_000L;
        ExecutorService pool = Executors.newFixedThreadPool(cfg.concurrency);
        AtomicLong total = new AtomicLong(0);
        AtomicLong ok = new AtomicLong(0);
        AtomicLong err = new AtomicLong(0);
        AtomicLong sumLatencyNs = new AtomicLong(0);
        Reservoir reservoir = new Reservoir(100_000);

        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < cfg.concurrency; i++) {
            final int threadIndex = i;
            tasks.add(() -> {
                // Deterministic request stream per (scenario, thread) for reproducibility across cache modes.
                SplittableRandom rnd = new SplittableRandom(mix64(seed ^ scenario.name.hashCode() ^ (threadIndex * 0x9E3779B97F4A7C15L)));
                while (System.nanoTime() < endAt) {
                    boolean isRead = rnd.nextDouble() < scenario.readRatio;
                    long id = 1L + (Math.floorMod(rnd.nextLong(), cfg.datasetSize));
                    long start = System.nanoTime();
                    try {
                        int status = isRead ? doRead(id) : doWrite(id, rnd);
                        long latency = System.nanoTime() - start;
                        total.incrementAndGet();
                        if (status / 100 == 2) {
                            ok.incrementAndGet();
                            if (collectLatency) {
                                sumLatencyNs.addAndGet(latency);
                                reservoir.record(latency);
                            }
                        } else {
                            err.incrementAndGet();
                        }
                    } catch (Exception e) {
                        total.incrementAndGet();
                        err.incrementAndGet();
                    }
                }
                return null;
            });
        }

        pool.invokeAll(tasks);
        pool.shutdown();
        pool.awaitTermination(30, TimeUnit.SECONDS);

        RunStats s = new RunStats();
        s.total = total.get();
        s.success = ok.get();
        s.errors = err.get();
        if (collectLatency && ok.get() > 0) {
            s.avgLatencyMs = (sumLatencyNs.get() / (double) ok.get()) / 1_000_000.0;
            long[] snap = reservoir.snapshot();
            if (snap.length > 0) {
                int idx = (int) Math.min(Math.floor(snap.length * 0.95), snap.length - 1);
                s.p95LatencyMs = snap[idx] / 1_000_000.0;
            }
        }
        return s;
    }

    private int doRead(long id) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(api.baseUrl() + "/products/" + id))
                .timeout(Duration.ofSeconds(3))
                .GET()
                .build();
        HttpResponse<Void> resp = api.http().send(req, HttpResponse.BodyHandlers.discarding());
        return resp.statusCode();
    }

    private int doWrite(long id, SplittableRandom rnd) throws Exception {
        UpdateProductRequest reqBody = new UpdateProductRequest(
                null,
                null,
                1 + rnd.nextInt(499)
        );
        String json = "{\"name\":null,\"priceCents\":null,\"stock\":" + reqBody.stock + "}";
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(api.baseUrl() + "/products/" + id))
                .timeout(Duration.ofSeconds(3))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(json))
                .build();
        HttpResponse<Void> resp = api.http().send(req, HttpResponse.BodyHandlers.discarding());
        return resp.statusCode();
    }

    private static long mix64(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }

    private static class UpdateProductRequest {
        final int stock;
        UpdateProductRequest(String name, Long priceCents, int stock) { this.stock = stock; }
    }
}

