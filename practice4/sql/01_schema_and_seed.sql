
CREATE TABLE IF NOT EXISTS accounts (
    id          BIGSERIAL PRIMARY KEY,
    name        TEXT NOT NULL,
    balance     NUMERIC(12,2) NOT NULL DEFAULT 0,
    version     INT NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS orders (
    id          BIGSERIAL PRIMARY KEY,
    account_id  BIGINT NOT NULL REFERENCES accounts(id),
    amount      NUMERIC(12,2) NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

TRUNCATE TABLE orders RESTART IDENTITY CASCADE;
TRUNCATE TABLE accounts RESTART IDENTITY CASCADE;

INSERT INTO accounts (name, balance) VALUES
    ('Alice', 1000.00),
    ('Bob',   500.00);

-- Для phantom read: начально один "заказ" у Alice (id=1 после seed)
INSERT INTO orders (account_id, amount) VALUES (1, 50.00);
