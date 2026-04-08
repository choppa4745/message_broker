# План реализации pigeonMQ

## Общий подход

Разработка ведётся инкрементально: от работающего прототипа `in-memory Pub/Sub` к полнофункциональному брокеру с персистентностью, гарантией доставки и дополнительными функциями.

Цель каждого этапа — получать систему, которую можно проверить (хотя бы интеграционно).

---

## Этап 1: Проектирование и документация

Сейчас выполнено: архитектура, структуры данных, протокол, стек, план реализации.

---

## Этап 2: In-Memory Pub/Sub (ядро)

**Цель**: минимальный брокер, работающий в оперативной памяти.

### Модули


| Модуль                | Класс (пример)                     | Что делает                                             |
| --------------------- | ---------------------------------- | ------------------------------------------------------ |
| Broker                | `com.pigeonmq.broker.Broker`       | Оркестратор ядра                                       |
| Topic Manager         | `com.pigeonmq.broker.Topic`        | Fan-out delivery                                       |
| Queue / Queue Manager | `com.pigeonmq.broker.MessageQueue` | конкурирующие consumer’ы (MVP можно в упрощённом виде) |
| Subscription Manager  | `com.pigeonmq.broker.Subscription` | регистрация подписок                                   |


### Задачи

1. Реализовать модели: `Message`, `Topic`, `Queue`, `Subscription`
2. Реализовать Pub/Sub семантику для топиков
3. Реализовать базовую маршрутизацию `publish` -> менеджеры
4. unit-тесты (JUnit 5) на fan-out и consumer offsets

### Результат

В памяти есть топики и подписчики, сообщения доставляются мгновенно (без сети и БД).

---

## Этап 3: MQTT server и протокол pigeonMQ

**Цель**: клиенты подключаются по MQTT и обмениваются publish/subscribe сообщениями (QoS 1).

### Модули


| Модуль         | Класс                                           | Что делает                          |
| -------------- | ----------------------------------------------- | ----------------------------------- |
| MQTT Server    | `com.pigeonmq.transport.mqtt.MqttServer`        | accept + сессии                     |
| MQTT Codec     | `com.pigeonmq.transport.mqtt.MqttCodec`         | encode/decode пакетов MQTT          |
| Client Session | `com.pigeonmq.transport.mqtt.MqttClientSession` | обработка CONNECT/SUBSCRIBE/PUBLISH |


### Задачи

1. Реализовать протокол по `[docs/protocol.md](protocol.md)` (mapping на MQTT destinations)
2. CONNECT/CONNACK, SUBSCRIBE/SUBACK, PUBLISH/PUBACK (QoS 1)
3. Реализовать shared subscriptions для очередей (если используете MQTT 5)
4. Протянуть delivery state из PostgreSQL: отправка outbound PUBLISH подписчикам
5. Keepalive (PING/PONG на уровне MQTT) и корректное закрытие соединения
6. Интеграционный тест: publisher + subscriber через MQTT

### Результат

Гарантий обработки (бизнес-ACK) может пока не быть, но уже есть полноценная сетевая коммуникация и базовая надёжность QoS 1.

---

## Этап 4: Очереди (Point-to-Point)

**Цель**: добавить point-to-point delivery с конкурирующими consumer’ами (MQTT shared subscriptions + delivery engine).

### Задачи

1. Очереди поддерживают FIFO и (позже) priority
2. Delivery выбирает одно сообщение для одного consumer’а
3. Наличие нескольких подписчиков на одну queue приводит к распределению доставок
4. unit-тесты на распределение и порядок

### Результат

Система поддерживает и топики (fan-out), и очереди (competing consumers).

---

## Этап 5: Персистентность (PostgreSQL Storage Engine)

**Цель**: сообщения и offsets сохраняются в PostgreSQL и восстанавливаются после рестарта.

### Модули


| Модуль         | Класс                           | Что делает                            |
| -------------- | ------------------------------- | ------------------------------------- |
| Repository/DAO | `com.pigeonmq.storage.`*        | CRUD для сообщений/offsets/state      |
| Migration      | `db/migrations`                 | создание схемы (Flyway или Liquibase) |
| Recovery       | `com.pigeonmq.storage.Recovery` | загрузка state после restart          |


### Задачи

1. Создать схему БД по `docs/data-structures.md`
2. Реализовать `publish` транзакционно:
  - инкремент `topics.next_offset` / `queues.next_offset`
  - вставка `topic_messages` / `queue_messages`
3. Реализовать `subscribe`:
  - upsert/insert в `subscriptions`
4. Реализовать выборку доставляемых сообщений:
  - topic: `offset > consumer_offset`
  - queue: `state = READY` + порядок (FIFO/priority)
5. Recovery:
  - topic offsets берутся из subscriptions
  - queue INFLIGHT возвращаются в READY, если `ack_deadline <= now()`

### Результат

Message durability: сообщения переживают рестарт брокера.

---

## Этап 6: ACK/NACK и at-least-once + DLQ

**Цель**: гарантия доставки.

### Задачи

1. Когда broker отправляет MESSAGE:
  - для topic ничего “claim” не требуется, достаточно проверки offset
  - для queue сообщение атомарно переводится в `INFLIGHT` с `ack_deadline`
2. ACK:
  - topic: `subscriptions.consumer_offset` обновляется
  - queue: INFLIGHT -> ACKED
3. NACK:
  - retry: INFLIGHT -> READY (или сразу в retry_count++)
  - DLQ: при превышении max_retries -> `dlq_messages`
4. Retry worker:
  - периодически выбирает `INFLIGHT` с истёкшим `ack_deadline` и requeue’ит

### Результат

at-least-once: сообщения не теряются.

---

## Этап 7: REST API и метрики

**Цель**: управление и мониторинг.

### Задачи

1. REST CRUD для `topics`/`queues`
2. Healthcheck endpoint
3. Prometheus metrics endpoint `/metrics`
4. Статистика:
  - размер topic/queue
  - число in-flight / DLQ

### Результат

Удобное администрирование и прозрачность системы.

---

## Этап 8: TTL и “бонусные” функции

### Задачи

1. TTL:
  - expires_at при publish
  - фильтрация при delivery
  - cleanup job (или soft-delete)
2. Priority для queue (если ещё не сделано)
3. Graceful shutdown:
  - остановить delivery workers
  - завершить in-flight операции (или корректно завершить транзакции)

---

## Этап 9: Java-клиент (SDK)

**Цель**: клиентская библиотека на Java с удобным API.

### Задачи

1. MQTT connect + keepalive
2. `Publisher.publish(...)` с обработкой ack/error
3. `Subscriber.subscribe(...)` + callback для `MESSAGE`
4. Auto-reconnect (при обрыве соединения)
5. Javadoc и примеры в `examples/`

---

## Этап 10: Тестовый стенд (Docker Compose + Postgres)

### Задачи

1. Контейнер `postgres`
2. Контейнер `broker`
3. Контейнеры `publisher`, `subscriber-1`, `subscriber-2` (опционально на MVP)
4. Volume для Postgres (чтобы данные переживали перезапуск контейнеров)

---

## Этап 11: Финальная документация и полировка

1. Финальная версия README + схемы
2. Дополнить API docs (Javadoc для SDK и REST)
3. Тестирование сценариев:
  - повторная доставка при отсутствии ACK
  - recovery после restart
  - DLQ при max_retries

---

## Покрытие критериев оценки


| Критерий                     | Макс. баллов | Этапы                     |
| ---------------------------- | ------------ | ------------------------- |
| Базовая функциональность     | 3            | Этапы 2, 3, 4             |
| Персистентность и надёжность | 1            | Этапы 5, 6                |
| Клиентская библиотека        | 1            | Этап 9                    |
| Документация и схема         | 1            | Этап 1 (текущий), Этап 11 |
| Дополнительные функции       | до 4         | Этапы 7, 8                |
| **Итого**                    | **до 10**    |                           |
