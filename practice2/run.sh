#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

echo "=== RabbitMQ vs Redis: Benchmark Suite ==="
echo ""

# ── Start brokers ───────────────────────────────────────
echo "Starting brokers…"
docker compose up -d

echo -n "  RabbitMQ: "
for i in $(seq 1 60); do
    if docker exec bench-rabbitmq rabbitmq-diagnostics -q ping &>/dev/null; then
        echo "ready"; break
    fi
    echo -n "."; sleep 2
done

echo -n "  Redis:    "
for i in $(seq 1 30); do
    if docker exec bench-redis redis-cli ping 2>/dev/null | grep -q PONG; then
        echo "ready"; break
    fi
    echo -n "."; sleep 1
done

# ── Build ───────────────────────────────────────────────
echo ""
echo "Building project…"
mvn clean package -q -DskipTests

# ── Run ─────────────────────────────────────────────────
echo ""
java -jar target/broker-benchmark-1.0.0.jar "$@"

echo ""
echo "=== Done! ==="
echo "Report:    results/report.md"
echo "Raw data:  results/raw_results.json"
