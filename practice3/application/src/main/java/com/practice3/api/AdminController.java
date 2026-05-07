package com.practice3.api;

import com.practice3.cache.RedisProductCache;
import com.practice3.metrics.AppMetrics;
import com.practice3.service.SeedService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/admin")
public class AdminController {

    private final SeedService seed;
    private final RedisProductCache cache;
    private final AppMetrics metrics;

    public AdminController(SeedService seed, RedisProductCache cache, AppMetrics metrics) {
        this.seed = seed;
        this.cache = cache;
        this.metrics = metrics;
    }

    @PostMapping("/reset")
    public void reset() {
        seed.reset();
        metrics.setWriteBackDirtyCount(cache.dirtyCount());
    }
}

