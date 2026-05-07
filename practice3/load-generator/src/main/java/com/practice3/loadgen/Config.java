package com.practice3.loadgen;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

public class Config {

    public final Map<String, String> targets;
    public final int datasetSize;
    public final int durationSeconds;
    public final int warmupSeconds;
    public final int concurrency;
    public final Path resultsDir;
    public final long randomSeed;

    private Config(Map<String, String> targets, int datasetSize, int durationSeconds, int warmupSeconds, int concurrency, Path resultsDir, long randomSeed) {
        this.targets = targets;
        this.datasetSize = datasetSize;
        this.durationSeconds = durationSeconds;
        this.warmupSeconds = warmupSeconds;
        this.concurrency = concurrency;
        this.resultsDir = resultsDir;
        this.randomSeed = randomSeed;
    }

    public static Config fromEnv() {
        String targetsRaw = env("TARGETS",
                "cache-aside=http://localhost:8086,write-through=http://localhost:8087,write-back=http://localhost:8088");
        Map<String, String> targets = parseTargets(targetsRaw);

        int datasetSize = Integer.parseInt(env("DATASET_SIZE", "10000"));
        int durationSeconds = Integer.parseInt(env("DURATION_SECONDS", "30"));
        int warmupSeconds = Integer.parseInt(env("WARMUP_SECONDS", "5"));
        int concurrency = Integer.parseInt(env("CONCURRENCY", "32"));
        Path resultsDir = Path.of(env("RESULTS_DIR", "results"));
        long seed = Long.parseLong(env("RANDOM_SEED", "42"));

        return new Config(targets, datasetSize, durationSeconds, warmupSeconds, concurrency, resultsDir, seed);
    }

    private static String env(String k, String dflt) {
        String v = System.getenv(k);
        return (v == null || v.isBlank()) ? dflt : v.trim();
    }

    private static Map<String, String> parseTargets(String raw) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String part : raw.split(",")) {
            String p = part.trim();
            if (p.isEmpty()) continue;
            String[] kv = p.split("=", 2);
            if (kv.length != 2) continue;
            m.put(kv[0].trim(), kv[1].trim());
        }
        return m;
    }
}

