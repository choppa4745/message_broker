# Архитектура pigeonMQ

## Обзор

`pigeonMQ` — монолитный брокер сообщений с чётким разделением на логические слои. Транспортный слой реализован через MQTT, а персистентность — через PostgreSQL.

Брокер написан на Java 17 и использует пул потоков (`ExecutorService`) для конкурентной обработки:

- приём MQTT-соединений
- обработка MQTT packet’ов (CONNECT/SUBSCRIBE/PUBLISH/QoS1/PUBACK)
- доставка сообщений подписчикам

Персистентность реализована через PostgreSQL.

## Общая схема

```
                    ┌────────────────────────────────────────────────────┐
                    │                        Клиенты                      │
                    │                                                     │
                    │  ┌─────────┐   ┌──────────┐   ┌──────────────┐    │
                    │  │Publisher │   │Subscriber│   │ REST/Admin   │    │
                    │  │ (MQTT)   │   │ (MQTT)     │   │ (HTTP)       │    │
                    │  └────┬────┘   └────┬─────┘   └──────┬───────┘    │
                    └──────┼─────────────┼────────────────┼────────────┘
                            │             │                │
                    ┌───────▼─────────────▼────────────────▼────────────┐
                    │                    Transport Layer                    │
                    │  MQTT Server (transport)             HTTP Server (REST) │
                    └─────────────┬──────────────────────────┬────────────┘
                                  │                          │
                    ┌─────────────▼──────────────────────────▼────────────┐
                    │                        Broker Core                       │
                    │                                                        │
                    │  Message Router  → Topic Manager → Subscription Manager  │
                    │                    → Queue Manager → Delivery Manager     │
                    └─────────────┬──────────────────────────┬────────────┘
                                  │                          │
                                  ▼                          ▼
                    ┌───────────────────────────▼───────────────────────────┐
                    │                       Storage Engine                     │
                    │                    PostgreSQL (durability + recovery)    │
                    └──────────────────────────────────────────────────────────┘
```

## Слои и ответственность

### 1) Transport Layer

Отвечает за сетевое взаимодействие:

- **MQTT Server**: принимает MQTT-соединения, обрабатывает CONNECT/SUBSCRIBE/PUBLISH/QoS1/PUBACK, доставляет outbound PUBLISH подписчикам.
- **HTTP Server**: REST API для управления топиками/очередями, healthcheck и `/metrics` для Prometheus.

### 2) Broker Core

Содержит бизнес-логику маршрутизации и доставки:

- **Message Router**
  - определяет destination (`topic` vs `queue`)
  - вызывает нужный менеджер
- **Topic Manager (Pub/Sub)**
  - при `publish` создаёт запись сообщения для топика
  - подписчики читают по своему `consumer_offset`
- **Queue Manager (point-to-point)**
  - при `publish` создаёт запись в очередь (с offset и приоритетом)
  - Delivery Manager выбирает следующее сообщение и “забирает” его в INFLIGHT
- **Subscription Manager**
  - хранит и обновляет активные подписки (`client_id`, destination, consumer_offset для topic)
- **Delivery Manager**
  - доставляет `MESSAGE` подписчикам
  - ожидает `ACK` до `ack_deadline`
  - повторяет доставку (retry) и отправляет в DLQ после `max_retries`

### 3) Storage Engine (PostgreSQL)

Персистентность и восстановление:

- **publish**: транзакционно записывает сообщение и обновляет offset destination
- **subscribe**: сохраняет subscription и стартовый `consumer_offset`
- **delivery/claim** (для очередей): транзакционно переводит сообщение из `READY` в `INFLIGHT`
- **ack**: фиксирует подтверждение (topic offset / queue state)
- **retry/dlq**: переводит сообщения при истечении `ack_deadline` и при превышении retry лимита

На рестарте брокер восстанавливает состояние из таблиц:

- topic offset подписчиков — из `subscriptions.consumer_offset`
- queue in-flight — по `queue_messages.state` и `ack_deadline`

## Жизненный цикл сообщения

### 1) Publish

1. Publisher отправляет MQTT `PUBLISH` (QoS 1) в topic (например, `orders`/`tasks`)
2. MQTT Server принимает packet и передаёт его в `Message Router`
3. Router вызывает `Topic Manager` или `Queue Manager`
4. Менеджер выполняет транзакцию в PostgreSQL:
  - инкремент offset destination
  - вставка сообщения в таблицы сообщений
5. Delivery триггерится (или доставка произойдёт при следующем цикле Delivery Manager)
6. Broker отвечает `PUBACK` publisher’у

### 2) Subscribe и Receive

**Topic (fan-out)**:

- Subscription фиксируется в БД
- Delivery Manager периодически/по событию выбирает сообщения `offset > consumer_offset`
- после `ACK` обновляет `subscriptions.consumer_offset`

**Queue (competing consumers)**:

- Delivery Manager выбирает следующую запись из очереди в порядке FIFO/priority
- внутри транзакции “claim” переводит сообщение в `INFLIGHT` с `ack_deadline` и `retry_count`
- затем отправляет `MESSAGE`
- после `ACK` сообщение переводится в `ACKED` (или удаляется из рабочей выборки)

### 3) Retry и DLQ

- Delivery Manager по таймеру выбирает INFLIGHT сообщения с `ack_deadline <= now()`
- если retry < `max_retries`: возвращает в `READY` и увеличивает попытку
- если retry >= `max_retries`: переносит в DLQ (отдельная таблица/статус)

## Конкурентная модель (Java 17)

Используем пул потоков и scheduled задачи:

- `ExecutorService` для MQTT read loops / обработки packet’ов
- отдельные scheduled задачи для:
  - retry worker
  - TTL reaper (опционально в отдельной задаче)
  - snapshot/cleanup (если нужно)
- потокобезопасные in-memory кеши только для нетребовательных данных (например, локальный кэш destination’ов)

## Конфигурация

В конфиге должны быть:

- MQTT listen port
- HTTP listen port
- PostgreSQL DSN
- delivery параметры (`ack_timeout`, `max_retries`, `worker_pool_size`)
- TTL параметры (`check_interval`, поведение по умолчанию)

Пример (идея):

```yaml
server:
  mqtt_port: 1883
  http_port: 8080

postgres:
  url: "jdbc:postgresql://postgres:5432/pigeonmq"
  user: "pigeonmq"
  password: "pigeonmq"

delivery:
  ack_timeout_seconds: 30
  max_retries: 5
  retry_backoff: "exponential"
  worker_pool_size: 64

ttl:
  check_interval_seconds: 60
  default_ttl_seconds: 0
```

## Тестовый стенд (docker-compose)

Для удобства этапа разработки:

- поднимаем контейнеры `broker` и `postgres`
- optional: 1 publisher + 2 subscriber в одном compose

Ожидаемая схема: брокер по MQTT -> публикует/доставляет сообщения -> состояние хранится в PostgreSQL.