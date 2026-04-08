# Структуры данных и схема PostgreSQL pigeonMQ (MQTT)

## 1) Сообщение (Message)

В `pigeonMQ` сообщение — набор полей, который publisher отправляет в destination (topic или queue), а broker доставляет subscribers:

```java
public record Message(
    UUID id,                          // назначает брокер (или клиент, если захотите)
    String destination,             // имя топика/очереди
    DestType destType,              // TOPIC | QUEUE
    byte[] key,                     // опционально
    Map<String, String> headers,   // произвольные метаданные
    byte[] payload,                // тело сообщения
    int priority,                  // 0..255 (актуально для QUEUE)
    long ttlSeconds,              // 0 = бессрочно
    long timestampNanos           // время приёма брокером (для отладки/метрик)
) {}
```

## 2) Destination: Topic / Queue

Destination — это имя и параметры хранения:

```text
Topic:
  - fan-out: каждую публикацию увидят все subscribers топика
  - потребитель читает по consumer_offset

Queue:
  - point-to-point: сообщение получит один consumer
  - поддержка FIFO и (опционально) priority
  - гарантия at-least-once реализуется через ACK/NACK + состояния INFLIGHT/DLQ
```

### Таблицы назначения

1. `topics`
  - `name TEXT PRIMARY KEY`
  - `created_at TIMESTAMPTZ NOT NULL`
  - `retention_ttl_seconds BIGINT NOT NULL DEFAULT 0`
  - `next_offset BIGINT NOT NULL`  (используется для monotonic offset)
2. `queues`
  - `name TEXT PRIMARY KEY`
  - `created_at TIMESTAMPTZ NOT NULL`
  - `mode TEXT NOT NULL`  (`FIFO` | `PRIORITY`)
  - `next_offset BIGINT NOT NULL`
  - `dlq_name TEXT` (опционально; можно реализовать как таблицу DLQ без отдельного имени)
  - `max_retries INT NOT NULL DEFAULT 5`

## 3) Хранилище сообщений

Мы разделяем семантики:

- **topic-messages** хранят только append-only сообщения и offset
- **queue-messages** хранят сообщения и их состояния доставки

### 3.1 Topic messages (append-only)

`topic_messages`

- `topic_name TEXT NOT NULL REFERENCES topics(name)`
- `offset BIGINT NOT NULL`
- `message_id UUID NOT NULL`
- `payload BYTEA NOT NULL`
- `key BYTEA`
- `headers JSONB`
- `priority INT` (для простоты можно хранить всегда)
- `timestamp TIMESTAMPTZ NOT NULL`
- `expires_at TIMESTAMPTZ` (NULL если TTL=0)

Ключ:

- `PRIMARY KEY (topic_name, offset)`

Индексы:

- `INDEX(topic_name, offset)`
- `INDEX(expires_at)` для TTL cleanup (опционально)

### 3.2 Queue messages (stateful)

`queue_messages`

- `queue_name TEXT NOT NULL REFERENCES queues(name)`
- `offset BIGINT NOT NULL`
- `message_id UUID NOT NULL`
- `payload BYTEA NOT NULL`
- `key BYTEA`
- `headers JSONB`
- `priority INT NOT NULL`
- `timestamp TIMESTAMPTZ NOT NULL`
- `expires_at TIMESTAMPTZ`

Состояния доставки (через отдельную таблицу, чтобы не усложнять запись в основной append-only журнал):

- `READY` — доступно для claim
- `INFLIGHT` — доставляется одному consumer’у и ждёт ACK
- `ACKED` — подтверждено (можно хранить как “история” или просто освобождать)
- `DLQ` — попало в dead-letter

Чтобы просто и надёжно реализовать at-least-once, используем таблицу состояния:

`queue_delivery_state`

- `queue_name TEXT NOT NULL`
- `message_id UUID NOT NULL`
- `consumer_id TEXT NOT NULL` (clientId из CONNECT)
- `state TEXT NOT NULL` (`INFLIGHT`/`ACKED`)
- `retry_count INT NOT NULL DEFAULT 0`
- `ack_deadline TIMESTAMPTZ NOT NULL`
- `last_delivery_at TIMESTAMPTZ NOT NULL`
- `attempt_no INT NOT NULL`

Ограничения/индексы:

- уникальность claim, например `UNIQUE(message_id)` или `UNIQUE(queue_name, message_id)` — чтобы одно сообщение одновременно не было INFLIGHT у двух consumer’ов
- индекс для поиска просроченных доставок: `INDEX(ack_deadline)`

### 3.3 Topic delivery state (at-least-once)

Для TOPIC fan-out делается каждому subscriber’у отдельно. Если вы используете application-level ACK (вариант B из `docs/protocol.md`), то для каждого subscriber’а нужен in-flight state аналогичный `queue_delivery_state`.

`topic_delivery_state`

- `topic_name TEXT NOT NULL`
- `message_id UUID NOT NULL`
- `client_id TEXT NOT NULL` (clientId из CONNECT)
- `state TEXT NOT NULL` (`INFLIGHT`/`ACKED`)
- `retry_count INT NOT NULL DEFAULT 0`
- `ack_deadline TIMESTAMPTZ NOT NULL`
- `last_delivery_at TIMESTAMPTZ NOT NULL`
- `attempt_no INT NOT NULL`

Ограничения/индексы:

- уникальность claim: `UNIQUE(topic_name, message_id, client_id)`
- индекс для поиска истёкших delivery: `INDEX(ack_deadline)`

## 4) Подписки (Subscriptions)

### Topic subscription

`subscriptions`

- `client_id TEXT NOT NULL`
- `dest_type TEXT NOT NULL` (`TOPIC` | `QUEUE`)
- `destination_name TEXT NOT NULL`
- `consumer_offset BIGINT NOT NULL DEFAULT 0`
- `created_at TIMESTAMPTZ NOT NULL`
- `last_seen_at TIMESTAMPTZ`

Для QUEUE обычно можно не хранить `consumer_offset`, потому что выбор следующего сообщения делается через claim в `queue_delivery_state`.

## 5) Dead Letter Queue (DLQ)

`dlq_messages`

- `message_id UUID NOT NULL`
- `dest_type TEXT NOT NULL` (`TOPIC` | `QUEUE`)
- `destination_name TEXT NOT NULL`
- `payload BYTEA NOT NULL`
- `headers JSONB`
- `priority INT`
- `original_offset BIGINT`
- `retry_count INT NOT NULL`
- `failed_at TIMESTAMPTZ NOT NULL`
- `reason TEXT`

Очевидная политика:

- при `retry_count >= max_retries` переносим сообщение из READY/INFLIGHT в DLQ

## 6) Как реализовать FIFO/priority на SQL уровне (упрощённо)

Для очереди с FIFO:

- claim выбирает минимальный `offset` среди `READY` (и не истёкших TTL)

Для очереди с priority:

- claim выбирает максимальный `priority`, а при равенстве — минимальный `offset`

В ранней версии (MVP) это делается одним транзакционным “claim” запросом:

- выбрать 1 кандидата (ordered)
- взять lock через `FOR UPDATE SKIP LOCKED` (если применимо) или атомарный переход статуса
- создать запись в `queue_delivery_state` с `ack_deadline`

Точный SQL будет частью реализации на этапе 5/6, но структура таблиц рассчитана на:

- at-least-once (ACK влияет на state)
- конкурирующих consumer’ов (claim в транзакциях)

## 7) TTL

TTL превращаем в `expires_at` при publish:

- если `ttlSeconds=0`, `expires_at = NULL`
- иначе `expires_at = now() + ttlSeconds`

При claim:

- выборка сообщений фильтрует `expires_at IS NULL OR expires_at > now()`

Cleanup:

- фоновый job удаляет устаревшие сообщения из topic_messages/queue_messages (или переводит в DLQ/статус, если нужна история)

## 8) Recovery после рестарта

Поскольку state и offsets в БД:

- подписки восстанавливают `consumer_offset` по `subscriptions`
- для очередей INFLIGHT восстанавливается из `queue_delivery_state`:
  - все INFLIGHT, у которых `ack_deadline <= now()`, возвращаются в READY (увеличивается retry) при следующем цикле delivery/retry worker

