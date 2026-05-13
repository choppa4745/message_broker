#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

echo "=== Practice3: cache strategy comparison ==="
echo ""

echo "Building images and starting stack…"
docker compose up -d --build postgres redis app-cache-aside app-write-through app-write-back

echo ""
echo "Waiting for apps to become healthy…"
docker compose ps

echo ""
echo "Running load-generator (will write to ./results)…"
docker compose run --rm loadgen

echo ""
echo "Done."
echo "Report:  practice3/results/report.md"
echo "JSON:    practice3/results/results.json"

