package com.practice3.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;
import org.springframework.stereotype.Component;

import jakarta.validation.constraints.Min;

@Component
@ConfigurationProperties(prefix = "practice3")
@Validated
public class Practice3Props {

    private final Dataset dataset = new Dataset();
    private final Cache cache = new Cache();
    private final WriteBack writeBack = new WriteBack();

    public Dataset getDataset() { return dataset; }
    public Cache getCache() { return cache; }
    public WriteBack getWriteBack() { return writeBack; }

    public static class Dataset {
        @Min(1)
        private int size = 10_000;
        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
    }

    public static class Cache {
        @Min(0)
        private long ttlSeconds = 60;
        public long getTtlSeconds() { return ttlSeconds; }
        public void setTtlSeconds(long ttlSeconds) { this.ttlSeconds = ttlSeconds; }
    }

    public static class WriteBack {
        @Min(10)
        private long flushIntervalMs = 500;
        @Min(1)
        private int flushBatchSize = 200;
        public long getFlushIntervalMs() { return flushIntervalMs; }
        public void setFlushIntervalMs(long flushIntervalMs) { this.flushIntervalMs = flushIntervalMs; }
        public int getFlushBatchSize() { return flushBatchSize; }
        public void setFlushBatchSize(int flushBatchSize) { this.flushBatchSize = flushBatchSize; }
    }
}

