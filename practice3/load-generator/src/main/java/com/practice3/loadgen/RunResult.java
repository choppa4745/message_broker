package com.practice3.loadgen;

import java.time.LocalDateTime;

public class RunResult {

    public String cacheMode;
    public String baseUrl;
    public String scenario;
    public double readRatio;
    public int durationSeconds;
    public int concurrency;
    public long datasetSize;
    public String timestamp;

    public long totalRequests;
    public long successRequests;
    public long errorRequests;
    public double throughputRps;
    public double avgLatencyMs;
    public double p95LatencyMs;

    public double dbReadsDelta;
    public double dbWritesDelta;
    public double cacheGetsDelta;
    public double cacheHitsDelta;
    public double cacheHitRate;

    public double writeBackDirtyEnd;
    public double writeBackFlushedDelta;
    public double writeBackEnqueuedDelta;
    public double writeBackFlushErrorsDelta;

    public static RunResult from(String mode, String baseUrl, Scenario sc, Config cfg, RunStats run,
                                 StatsSnapshot before, StatsSnapshot after) {
        RunResult r = new RunResult();
        r.cacheMode = mode;
        r.baseUrl = baseUrl;
        r.scenario = sc.name;
        r.readRatio = sc.readRatio;
        r.durationSeconds = cfg.durationSeconds;
        r.concurrency = cfg.concurrency;
        r.datasetSize = cfg.datasetSize;
        r.timestamp = LocalDateTime.now().toString();

        r.totalRequests = run.total;
        r.successRequests = run.success;
        r.errorRequests = run.errors;
        r.throughputRps = cfg.durationSeconds > 0 ? (run.success / (double) cfg.durationSeconds) : 0;
        r.avgLatencyMs = run.avgLatencyMs;
        r.p95LatencyMs = run.p95LatencyMs;

        r.dbReadsDelta = after.dbReads - before.dbReads;
        r.dbWritesDelta = after.dbWrites - before.dbWrites;
        r.cacheGetsDelta = after.cacheGets - before.cacheGets;
        r.cacheHitsDelta = after.cacheHits - before.cacheHits;
        r.cacheHitRate = r.cacheGetsDelta > 0 ? (r.cacheHitsDelta / r.cacheGetsDelta) : 0;

        r.writeBackDirtyEnd = after.writeBackDirtyCount;
        r.writeBackFlushedDelta = after.writeBackFlushed - before.writeBackFlushed;
        r.writeBackEnqueuedDelta = after.writeBackEnqueued - before.writeBackEnqueued;
        r.writeBackFlushErrorsDelta = after.writeBackFlushErrors - before.writeBackFlushErrors;
        return r;
    }
}

