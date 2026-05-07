# Практика 3: сравнение типов кеширования

## Цель

Сравнить три стратегии кеширования одной и той же системы в одинаковых условиях:

- **Lazy Loading / Cache-Aside / Write-Around** (`cache-aside`)
- **Write-Through** (`write-through`)
- **Write-Back** (`write-back`)

## Состав стенда

- **application**: Spring Boot 3 + REST API
- **cache**: Redis
- **DB**: PostgreSQL
- **load-generator**: самописный Java-нагрузчик (единый тест для всех вариантов)

Меняется только стратегия работы с кешем (через разные `SPRING_PROFILES_ACTIVE`).

## API приложения

- `GET /products/{id}` — чтение продукта
- `PUT /products/{id}` — запись (частичное обновление полей)
- `POST /admin/reset` — пересоздать датасет и очистить кеш (для честного старта)
- `GET /stats` — счётчики для отчёта (DB reads/writes, cache hit rate, write-back dirty queue)
- `GET /actuator/prometheus` — Prometheus метрики (дополнительно)

## Датасет

Приложение при старте гарантирует наличие таблицы `products` и заполняет её **детерминированно** (фиксированный seed) до `DATASET_SIZE`.

Это важно, чтобы во всех прогонах были одинаковые данные.

## Что измеряем

Для каждой стратегии и сценария:

- **throughput** (`req/sec`) — со стороны `load-generator`
- **avg latency** — со стороны `load-generator`
- **p95 latency** — со стороны `load-generator`
- **DB reads/writes** — счётчики приложения (через `/stats`)
- **cache hit rate** — `cacheHits / cacheGets` по счётчикам приложения
- **Write-Back dirty end** — сколько “грязных” записей осталось в кеше в конце прогона

## Сценарии нагрузки (единый тест)

Выполняются 3 прогона для каждого режима кеша:

- `read-heavy`: 80% read / 20% write
- `balanced`: 50% read / 50% write
- `write-heavy`: 20% read / 80% write

## Запуск

Требования:

- Docker + Docker Compose v2

Запуск одной командой:

```bash
./run.sh
```

Результаты сохраняются в `practice3/results/`:

- `report.md` — итоговый отчёт (таблица)
- `results.json` — сырые результаты прогонов

## Конфигурация (ENV)

Основные переменные (в `docker-compose.yml` уже заданы):

- `DATASET_SIZE` — размер датасета (по умолчанию 10000)
- `CACHE_TTL_SECONDS` — TTL для значений в Redis
- `WRITEBACK_FLUSH_INTERVAL_MS` — интервал flush для write-back
- `WRITEBACK_FLUSH_BATCH_SIZE` — batch-size flush для write-back

Для load-generator:

- `DURATION_SECONDS`, `WARMUP_SECONDS`, `CONCURRENCY`
- `TARGETS` — список `mode=url` (3 инстанса приложения)

