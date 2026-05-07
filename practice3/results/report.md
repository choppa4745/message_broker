# Practice3 — сравнение типов кеширования

## Конфигурация теста

- **dataset**: 10,000 products
- **duration**: 30 seconds
- **warmup**: 5 seconds
- **concurrency**: 32 threads
- **random seed**: 42

## Результаты


| Cache mode    | Scenario    | Throughput (req/s) | Avg latency (ms) | P95 latency (ms) | Cache hit rate | DB reads | DB writes | WB dirty end | WB flush errors |
| ------------- | ----------- | ------------------ | ---------------- | ---------------- | -------------- | -------- | --------- | ------------ | --------------- |
| cache-aside   | balanced    | 3109               | 10.30            | 49.87            | 49.76%         | 84307    | 57655     | 0            | 0               |
| write-back    | balanced    | 4134               | 7.74             | 39.43            | 99.91%         | 137      | 7109      | 8988         | 0               |
| write-through | balanced    | 2562               | 12.38            | 40.21            | 99.86%         | 42659    | 42597     | 0            | 0               |
| cache-aside   | read-heavy  | 1718               | 18.62            | 71.38            | 64.79%         | 60402    | 33879     | 0            | 0               |
| write-back    | read-heavy  | 2383               | 13.44            | 49.14            | 94.35%         | 5182     | 3966      | 5962         | 0               |
| write-through | read-heavy  | 2171               | 14.73            | 44.59            | 92.31%         | 18850    | 14464     | 0            | 0               |
| cache-aside   | write-heavy | 2506               | 12.77            | 39.08            | 38.86%         | 80024    | 69518     | 0            | 0               |
| write-back    | write-heavy | 3177               | 10.07            | 43.28            | 100.00%        | 0        | 7166      | 9234         | 0               |
| write-through | write-heavy | 2817               | 11.34            | 42.79            | 99.92%         | 70771    | 70757     | 0            | 0               |


## Выводы (что смотреть)

- **Read-heavy**: чаще всего выигрывает стратегия с максимальным cache hit rate и минимальными DB reads.
- **Write-heavy**: сравните DB writes и latency; у write-back DB writes могут быть ниже в моменте, но растёт `WB dirty end`.
- **Balanced**: смотрите компромисс throughput/latency и суммарную нагрузку на БД.

Отдельно для **Write-Back**: если `WB dirty end` растёт — это означает накопление “грязных” записей, которые ещё не дошли до БД.

### Важное ограничение бенчмарка

Нагрузчик работает в **closed-loop** режиме (следующий запрос после ответа). Это может скрывать хвосты задержек (coordinated omission). Для production-grade бенчмарка используйте open-loop rate (k6/JMeter) или планировщик запросов по времени.