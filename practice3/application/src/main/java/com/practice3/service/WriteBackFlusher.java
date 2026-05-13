package com.practice3.service;

import com.practice3.cache.RedisProductCache;
import com.practice3.config.Practice3Props;
import com.practice3.db.ProductRepository;
import com.practice3.metrics.AppMetrics;
import com.practice3.model.Product;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@Profile("write-back")
public class WriteBackFlusher {

    private final RedisProductCache cache;
    private final ProductRepository repo;
    private final Practice3Props props;
    private final AppMetrics metrics;

    public WriteBackFlusher(RedisProductCache cache, ProductRepository repo, Practice3Props props, AppMetrics metrics) {
        this.cache = cache;
        this.repo = repo;
        this.props = props;
        this.metrics = metrics;
    }

    @Scheduled(fixedDelayString = "${practice3.writeBack.flushIntervalMs:500}")
    @Transactional
    public void flush() {
        int batchSize = props.getWriteBack().getFlushBatchSize();
        List<String> batch = cache.scanDirtyBatch(batchSize);
        if (batch == null || batch.isEmpty()) {
            metrics.setWriteBackDirtyCount(cache.dirtyCount());
            return;
        }
        metrics.writeBackFlushBatches.increment();
        for (String idStr : batch) {
            try {
                long id = Long.parseLong(idStr);
                Product p = cache.get(id).orElse(null);
                if (p == null) {
                    // If payload is missing, we can't flush it. Remove from dirty set to avoid infinite loop.
                    cache.removeDirty(idStr);
                    continue;
                }
                repo.upsert(p);
                cache.removeDirty(idStr); // ACK only after DB write succeeds
                metrics.writeBackFlushed.increment();
            } catch (Exception e) {
                metrics.writeBackFlushErrors.increment();
                // keep dirty marker for retry
            }
        }
        metrics.setWriteBackDirtyCount(cache.dirtyCount());
    }
}

