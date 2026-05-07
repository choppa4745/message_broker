package com.practice3.service;

import com.practice3.cache.RedisProductCache;
import com.practice3.config.Practice3Props;
import com.practice3.db.ProductRepository;
import com.practice3.model.Product;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Component
public class SeedService implements ApplicationRunner {

    private final ProductRepository repo;
    private final RedisProductCache cache;
    private final Practice3Props props;

    private static final long SEED_LOCK_KEY = 3_030_303L;

    public SeedService(ProductRepository repo, RedisProductCache cache, Practice3Props props) {
        this.repo = repo;
        this.cache = cache;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        repo.createTableIfMissing();
        ensureSeeded();
    }

    public void ensureSeeded() {
        int n = props.getDataset().getSize();
        if (repo.count() == n) return;

        // Multiple app instances start in parallel. Guard seed with an advisory lock.
        waitAndRunWithLock(() -> {
            if (repo.count() == n) return;
            repo.truncate();
            repo.batchInsert(generateDeterministicProducts(n));
            cache.clearAllProducts();
        });
    }

    public void reset() {
        int n = props.getDataset().getSize();
        waitAndRunWithLock(() -> {
            repo.truncate();
            repo.batchInsert(generateDeterministicProducts(n));
            cache.clearAllProducts();
        });
    }

    private void waitAndRunWithLock(Runnable action) {
        long deadline = System.currentTimeMillis() + 30_000;
        boolean locked = false;
        while (System.currentTimeMillis() < deadline) {
            locked = repo.tryAdvisoryLock(SEED_LOCK_KEY);
            if (locked) break;
            try { Thread.sleep(200); } catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
        }
        if (!locked) {
            // If we couldn't lock, just skip to avoid crashing the app.
            return;
        }
        try {
            action.run();
        } finally {
            try { repo.advisoryUnlock(SEED_LOCK_KEY); } catch (Exception ignored) {}
        }
    }

    private static List<Product> generateDeterministicProducts(int n) {
        Random rnd = new Random(42);
        List<Product> products = new ArrayList<>(n);
        for (int i = 1; i <= n; i++) {
            long id = i;
            String name = "Product-" + i;
            long price = 100 + rnd.nextInt(50_000);
            int stock = 1 + rnd.nextInt(500);
            products.add(new Product(id, name, price, stock, 1));
        }
        return products;
    }
}

