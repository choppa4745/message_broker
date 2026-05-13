package com.practice3.api;

public record StatsResponse(
        String cacheMode,
        long datasetSize,
        double httpReads,
        double httpWrites,
        double httpErrors,
        double dbReads,
        double dbWrites,
        double cacheGets,
        double cacheHits,
        double cacheSets,
        double cacheDeletes,
        double writeBackDirtyCount,
        double writeBackEnqueued,
        double writeBackFlushed,
        double writeBackFlushBatches,
        double writeBackFlushErrors
) {}

