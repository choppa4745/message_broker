package com.practice3.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

@Component
public class AppMetrics {

    public final Counter httpReads;
    public final Counter httpWrites;
    public final Counter httpErrors;

    public final Counter dbReads;
    public final Counter dbWrites;

    public final Counter cacheGets;
    public final Counter cacheHits;
    public final Counter cacheSets;
    public final Counter cacheDeletes;

    public final Counter writeBackEnqueued;
    public final Counter writeBackFlushed;
    public final Counter writeBackFlushBatches;
    public final Counter writeBackFlushErrors;

    private final AtomicLong writeBackDirtyGauge = new AtomicLong(0);
    @SuppressWarnings("unused")
    private final Gauge writeBackDirtyGaugeMeter;

    public AppMetrics(MeterRegistry registry) {
        this.httpReads = registry.counter("practice3_http_reads_total");
        this.httpWrites = registry.counter("practice3_http_writes_total");
        this.httpErrors = registry.counter("practice3_http_errors_total");

        this.dbReads = registry.counter("practice3_db_reads_total");
        this.dbWrites = registry.counter("practice3_db_writes_total");

        this.cacheGets = registry.counter("practice3_cache_gets_total");
        this.cacheHits = registry.counter("practice3_cache_hits_total");
        this.cacheSets = registry.counter("practice3_cache_sets_total");
        this.cacheDeletes = registry.counter("practice3_cache_deletes_total");

        this.writeBackEnqueued = registry.counter("practice3_writeback_enqueued_total");
        this.writeBackFlushed = registry.counter("practice3_writeback_flushed_total");
        this.writeBackFlushBatches = registry.counter("practice3_writeback_flush_batches_total");
        this.writeBackFlushErrors = registry.counter("practice3_writeback_flush_errors_total");

        this.writeBackDirtyGaugeMeter = Gauge.builder("practice3_writeback_dirty_count", writeBackDirtyGauge, AtomicLong::get)
                .register(registry);
    }

    public void setWriteBackDirtyCount(long v) {
        writeBackDirtyGauge.set(v);
    }

    public long getWriteBackDirtyCount() {
        return writeBackDirtyGauge.get();
    }
}

