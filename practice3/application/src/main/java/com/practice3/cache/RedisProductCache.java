package com.practice3.cache;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.practice3.metrics.AppMetrics;
import com.practice3.model.Product;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Component
public class RedisProductCache {

    public static final String PRODUCT_KEY_PREFIX = "practice3:product:";
    public static final String DIRTY_SET_KEY = "practice3:writeback:dirty";

    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final AppMetrics metrics;

    public RedisProductCache(StringRedisTemplate redis, ObjectMapper mapper, AppMetrics metrics) {
        this.redis = redis;
        this.mapper = mapper;
        this.metrics = metrics;
    }

    public Optional<Product> get(long id) {
        metrics.cacheGets.increment();
        String v = redis.opsForValue().get(productKey(id));
        if (v == null) return Optional.empty();
        metrics.cacheHits.increment();
        try {
            return Optional.of(mapper.readValue(v, Product.class));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public void set(long id, Product p, Duration ttl) {
        try {
            String json = mapper.writeValueAsString(p);
            metrics.cacheSets.increment();
            if (ttl != null && !ttl.isZero() && !ttl.isNegative()) {
                redis.opsForValue().set(productKey(id), json, ttl);
            } else {
                redis.opsForValue().set(productKey(id), json);
            }
        } catch (JsonProcessingException ignored) {}
    }

    public void delete(long id) {
        metrics.cacheDeletes.increment();
        redis.delete(productKey(id));
    }

    public void clearAllProducts() {
        // Avoid KEYS in production (blocks Redis). Use SCAN + UNLINK batches.
        scanAndUnlink(PRODUCT_KEY_PREFIX + "*", 1_000);
        redis.unlink(DIRTY_SET_KEY);
    }

    public void markDirty(long id) {
        redis.opsForSet().add(DIRTY_SET_KEY, String.valueOf(id));
        metrics.writeBackEnqueued.increment();
    }

    public List<String> scanDirtyBatch(int batchSize) {
        return scanSetMembers(DIRTY_SET_KEY, batchSize);
    }

    public void removeDirty(String id) {
        redis.opsForSet().remove(DIRTY_SET_KEY, id);
    }

    public long dirtyCount() {
        Long n = redis.opsForSet().size(DIRTY_SET_KEY);
        return n == null ? 0 : n;
    }

    private static String productKey(long id) {
        return PRODUCT_KEY_PREFIX + id;
    }

    private void scanAndUnlink(String pattern, int count) {
        redis.execute((RedisConnection connection) -> {
            ScanOptions options = ScanOptions.scanOptions().match(pattern).count(count).build();
            try (Cursor<byte[]> cursor = connection.scan(options)) {
                List<byte[]> batch = new ArrayList<>(count);
                while (cursor.hasNext()) {
                    batch.add(cursor.next());
                    if (batch.size() >= count) {
                        connection.unlink(batch.toArray(new byte[0][]));
                        batch.clear();
                    }
                }
                if (!batch.isEmpty()) {
                    connection.unlink(batch.toArray(new byte[0][]));
                }
            }
            return null;
        });
    }

    private List<String> scanSetMembers(String setKey, int count) throws DataAccessException {
        return redis.execute((RedisConnection connection) -> {
            ScanOptions options = ScanOptions.scanOptions().count(count).build();
            List<String> out = new ArrayList<>(count);
            byte[] rawKey = setKey.getBytes(StandardCharsets.UTF_8);
            try (Cursor<byte[]> cursor = connection.sScan(rawKey, options)) {
                while (cursor.hasNext() && out.size() < count) {
                    out.add(new String(cursor.next(), StandardCharsets.UTF_8));
                }
            }
            return out;
        });
    }
}

