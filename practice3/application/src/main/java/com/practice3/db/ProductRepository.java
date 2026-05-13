package com.practice3.db;

import com.practice3.metrics.AppMetrics;
import com.practice3.model.Product;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class ProductRepository {

    private final JdbcTemplate jdbc;
    private final AppMetrics metrics;

    private static final RowMapper<Product> MAPPER = (rs, rn) -> new Product(
            rs.getLong("id"),
            rs.getString("name"),
            rs.getLong("price_cents"),
            rs.getInt("stock"),
            rs.getLong("version")
    );

    public ProductRepository(JdbcTemplate jdbc, AppMetrics metrics) {
        this.jdbc = jdbc;
        this.metrics = metrics;
    }

    public void createTableIfMissing() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS products(
                    id BIGINT PRIMARY KEY,
                    name TEXT NOT NULL,
                    price_cents BIGINT NOT NULL,
                    stock INT NOT NULL,
                    version BIGINT NOT NULL
                )
                """);
    }

    public long count() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM products", Long.class);
    }

    public void truncate() {
        jdbc.execute("TRUNCATE TABLE products");
    }

    public void batchInsert(List<Product> products) {
        jdbc.batchUpdate(
                "INSERT INTO products(id, name, price_cents, stock, version) VALUES (?,?,?,?,?)",
                products,
                500,
                (ps, p) -> {
                    ps.setLong(1, p.id());
                    ps.setString(2, p.name());
                    ps.setLong(3, p.priceCents());
                    ps.setInt(4, p.stock());
                    ps.setLong(5, p.version());
                }
        );
    }

    public boolean tryAdvisoryLock(long lockKey) {
        Boolean v = jdbc.queryForObject("SELECT pg_try_advisory_lock(?)", Boolean.class, lockKey);
        return Boolean.TRUE.equals(v);
    }

    public void advisoryUnlock(long lockKey) {
        jdbc.queryForObject("SELECT pg_advisory_unlock(?)", Boolean.class, lockKey);
    }

    public Optional<Product> findById(long id) {
        metrics.dbReads.increment();
        List<Product> list = jdbc.query("SELECT * FROM products WHERE id = ?", MAPPER, id);
        return list.stream().findFirst();
    }

    public Product upsert(Product p) {
        metrics.dbWrites.increment();
        jdbc.update("""
                        INSERT INTO products(id, name, price_cents, stock, version)
                        VALUES (?,?,?,?,?)
                        ON CONFLICT (id) DO UPDATE SET
                          name = EXCLUDED.name,
                          price_cents = EXCLUDED.price_cents,
                          stock = EXCLUDED.stock,
                          version = EXCLUDED.version
                        """,
                p.id(), p.name(), p.priceCents(), p.stock(), p.version());
        return p;
    }
}

