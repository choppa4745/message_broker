package com.practice3.service;

import com.practice3.api.ProductDto;
import com.practice3.api.UpdateProductRequest;
import com.practice3.cache.RedisProductCache;
import com.practice3.config.Practice3Props;
import com.practice3.db.ProductRepository;
import com.practice3.metrics.AppMetrics;
import com.practice3.model.Product;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;

@Service
@Profile("write-through")
public class WriteThroughProductService implements ProductService {

    private final ProductRepository repo;
    private final RedisProductCache cache;
    private final Practice3Props props;
    private final SeedService seed;
    private final AppMetrics metrics;

    public WriteThroughProductService(ProductRepository repo,
                                     RedisProductCache cache,
                                     Practice3Props props,
                                     SeedService seed,
                                     AppMetrics metrics) {
        this.repo = repo;
        this.cache = cache;
        this.props = props;
        this.seed = seed;
        this.metrics = metrics;
    }

    @Override
    @Transactional(readOnly = true)
    public ProductDto getById(long id) {
        metrics.httpReads.increment();
        return cache.get(id)
                .map(ProductMapper::toDto)
                .orElseGet(() -> {
                    Product p = repo.findById(id).orElseThrow();
                    cache.set(id, p, Duration.ofSeconds(props.getCache().getTtlSeconds()));
                    return ProductMapper.toDto(p);
                });
    }

    @Override
    @Transactional
    public ProductDto update(long id, UpdateProductRequest req) {
        metrics.httpWrites.increment();
        Product current = repo.findById(id).orElseThrow();
        Product updated = new Product(
                id,
                req.name() != null ? req.name() : current.name(),
                req.priceCents() != null ? req.priceCents() : current.priceCents(),
                req.stock() != null ? req.stock() : current.stock(),
                current.version() + 1
        );
        repo.upsert(updated);
        cache.set(id, updated, Duration.ofSeconds(props.getCache().getTtlSeconds())); // write-through: update cache immediately
        return ProductMapper.toDto(updated);
    }

    @Override
    public void resetData() {
        seed.reset();
    }
}

