package com.practice3.loadgen;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class LoadGenApp {

    public static void main(String[] args) throws Exception {
        Config cfg = Config.fromEnv();
        System.out.println("=== practice3 load-generator ===");
        System.out.println("Targets: " + cfg.targets);
        System.out.println("Dataset size: " + cfg.datasetSize);
        System.out.println("Duration: " + cfg.durationSeconds + "s, warmup: " + cfg.warmupSeconds + "s");
        System.out.println("Concurrency: " + cfg.concurrency);
        System.out.println("Random seed: " + cfg.randomSeed);
        System.out.println();

        List<Scenario> scenarios = List.of(
                new Scenario("read-heavy", 0.80),
                new Scenario("balanced", 0.50),
                new Scenario("write-heavy", 0.20)
        );

        List<RunResult> results = new ArrayList<>();
        for (Map.Entry<String, String> target : cfg.targets.entrySet()) {
            String mode = target.getKey();
            String baseUrl = target.getValue();

            System.out.println("──────────────────────────────────────────────");
            System.out.println("Target: " + mode + " → " + baseUrl);
            System.out.println("──────────────────────────────────────────────");

            ApiClient api = new ApiClient(baseUrl);

            // Keep conditions comparable between modes: same initial dataset + empty cache.
            api.reset();
            Thread.sleep(500);

            for (Scenario sc : scenarios) {
                System.out.println();
                System.out.printf("▶ Scenario: %s  (read=%.0f%% / write=%.0f%%)%n",
                        sc.name, sc.readRatio * 100.0, (1.0 - sc.readRatio) * 100.0);

                StatsSnapshot before = api.stats();
                ScenarioRunner runner = new ScenarioRunner(cfg, api, sc, cfg.randomSeed);
                if (cfg.warmupSeconds > 0) {
                    runner.runWarmup();
                }
                RunStats run = runner.runMeasured();
                StatsSnapshot after = api.stats();

                RunResult rr = RunResult.from(mode, baseUrl, sc, cfg, run, before, after);
                results.add(rr);

                System.out.printf("  req=%,d ok=%,d err=%,d | thr=%.0f req/s | avg=%.2f ms | p95=%.2f ms%n",
                        rr.totalRequests, rr.successRequests, rr.errorRequests,
                        rr.throughputRps, rr.avgLatencyMs, rr.p95LatencyMs);

                System.out.printf("  cache hit=%.2f%% (hits=%,.0f / gets=%,.0f) | dbReads=%,.0f dbWrites=%,.0f",
                        rr.cacheHitRate * 100.0,
                        rr.cacheHitsDelta, rr.cacheGetsDelta,
                        rr.dbReadsDelta, rr.dbWritesDelta);
                if ("write-back".equals(mode)) {
                    System.out.printf(" | writebackDirtyEnd=%,.0f%n", rr.writeBackDirtyEnd);
                } else {
                    System.out.println();
                }
            }
        }

        Path outDir = cfg.resultsDir;
        Files.createDirectories(outDir);

        Gson gson = new GsonBuilder().setPrettyPrinting().create();
        String json = gson.toJson(results);
        Files.writeString(outDir.resolve("results.json"), json);

        String md = Report.renderMarkdown(results, cfg);
        Files.writeString(outDir.resolve("report.md"), md);

        System.out.println();
        System.out.println("Saved:");
        System.out.println("  " + outDir.resolve("results.json"));
        System.out.println("  " + outDir.resolve("report.md"));
        System.out.println("  finished at " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
    }
}

