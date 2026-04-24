# pigeonMQ — полное руководство по тестированию и разбору функциональности

Этот документ — одновременно и **инструкция по полному тестированию**, и **подробное объяснение того, как устроено приложение и что за что отвечает**.

Он построен так:

1. Что такое pigeonMQ и какие у него компоненты
2. Как поднять стенд
3. Базовые проверки (HTTP, MQTT)
4. REST API (управление топиками/очередями)
5. MQTT (ядро брокера): topic fan-out и queue competing consumers
6. Персистентность в PostgreSQL
7. At-least-once (гарантия доставки при рестарте/крэше)
8. Восстановление offset’ов для топиков
9. SQL-инспекция БД
10. Карта “что происходит внутри брокера” (приложение покомпонентно)

---

## 1. Что такое pigeonMQ

pigeonMQ — это брокер сообщений с двумя транспортами:

- **MQTT (порт 1883)** — основной протокол публикации/подписки. Работает на Netty.
- **HTTP REST (порт 8080)** — управление (создание/удаление топиков и очередей, health).

Хранение данных:

- **In-memory** (горячий путь доставки): `TopicStore`, `QueueStore`, `ClientSession`.
- **PostgreSQL** (источник правды): таблицы `destinations`, `messages`, `consumer_offsets`.

Ключевые идеи:

- Топик (`topic`) — **fan-out**: каждое сообщение получают **все** подписчики.
- Очередь (`queue`) — **competing consumers**: каждое сообщение получает **только один** потребитель (реализовано через MQTT Shared Subscriptions: `$share/<group>/<dest>`).
- Гарантия доставки **at-least-once**: сообщение может быть доставлено повторно при сбоях, но не потеряется.

---

## 2. Подготовка стенда

### 2.1 Требования

- Docker + Docker Compose
- mosquitto-clients (`mosquitto_pub`, `mosquitto_sub`)
- curl
- (опционально) DBeaver/psql — для просмотра БД

### 2.2 Запуск

Из корня проекта:

```bash
docker compose up -d --build broker
docker compose ps
```

Должно быть два контейнера в статусе `Up`:

- `pigeonmq-broker` — слушает `1883` (MQTT) и `8080` (HTTP)
- `pigeonmq-postgres` — слушает `5432`

Посмотреть логи:

```bash
docker compose logs -f broker
```

Признаки корректного старта:

- `Flyway ... Successfully applied 1 migration` (или `Schema "public" is up to date`)
- `Restored topics from DB: N`
- `Restored queues from DB: N`
- `Delivery scheduler started`
- `MQTT transport listening on port 1883`
- `Tomcat started on port 8080`

---

## 3. Базовые проверки (smoke)

### 3.1 HTTP работает

```bash
curl -s -i http://localhost:8080/health
```

Ожидаемо: `HTTP/1.1 200` и JSON вида:

```json
{"status":"UP","topicCount":0,"queueCount":0,"activeConnections":0}
```

### 3.2 MQTT слушает

```bash
mosquitto_pub -h localhost -p 1883 -q 1 -t ping -m "hello"
```

Ожидаемо: команда завершается без ошибки (брокер примет публикацию, даже если подписчиков ещё нет).

---

## 4. REST API (управление)

Есть три ресурса: `/health`, `/api/topics`, `/api/queues`.
Все запросы удобно смотреть в Swagger UI: `http://localhost:8080/swagger-ui`.

### 4.1 Создать топик

```bash
curl -s -X POST http://localhost:8080/api/topics \
  -H 'Content-Type: application/json' \
  -d '{"name":"orders"}'
```

Ожидаемо: `201 Created`, тело `{"success":true,"name":"orders",...}`.

### 4.2 Список топиков

```bash
curl -s http://localhost:8080/api/topics
```

### 4.3 Создать очередь

```bash
# FIFO
curl -s -X POST http://localhost:8080/api/queues \
  -H 'Content-Type: application/json' \
  -d '{"name":"emails","mode":"FIFO"}'

# PRIORITY
curl -s -X POST http://localhost:8080/api/queues \
  -H 'Content-Type: application/json' \
  -d '{"name":"tasks","mode":"PRIORITY"}'
```

### 4.4 Удалить

```bash
curl -s -X DELETE http://localhost:8080/api/topics/orders
curl -s -X DELETE http://localhost:8080/api/queues/emails
```

### 4.5 Ошибки

- `GET /api/topics/nonexistent` → `404`
- `POST /api/topics {"name":""}` → `400` (валидация DTO)
- `POST /api/queues {"name":"x","mode":"LIFO"}` → `400`

Что за это отвечает в коде:

- `controller/TopicController.java`, `QueueController.java`, `HealthController.java`
- `dto/*` (request/response DTO)
- `exception/ResourceNotFoundException`, `InvalidRequestException`
- `controller/GlobalExceptionHandler` — превращает исключения в корректные HTTP-ответы
- `service/TopicService`, `service/QueueService` — бизнес-логика

---

## 5. MQTT — ядро брокера

### 5.1 Topic (fan-out)

**Смысл**: каждый подписчик получает каждое сообщение.

Два подписчика:

Окно A:

```bash
mosquitto_sub -h localhost -p 1883 -q 1 -i subA -t t_fan
```

Окно B:

```bash
mosquitto_sub -h localhost -p 1883 -q 1 -i subB -t t_fan
```

Публикация:

```bash
mosquitto_pub -h localhost -p 1883 -q 1 -t t_fan -m "hello"
```

Ожидаемо: `hello` приходит **в оба окна**.

**Как это работает внутри**:

- `MqttPacketHandler` принимает пакет SUBSCRIBE → `BrokerFacade.subscribe()`
- Для обычного фильтра без `$share/` — добавляется в `ClientSession.subscribedTopics`
- На PUBLISH `TopicService.publish()` кладёт сообщение в `TopicStore` (offset-лог) и в БД (`messages.status=READY`)
- `DeliveryService` делает fan-out: для каждого подписанного клиента отправляет сообщения, начиная с его `topicSentOffset`

### 5.2 Queue (competing consumers)

**Смысл**: каждое сообщение получает **ровно один** потребитель.

Два consumer’а:

```bash
mosquitto_sub -h localhost -p 1883 -q 1 -i cA -t '$share/q_comp/q_comp'
mosquitto_sub -h localhost -p 1883 -q 1 -i cB -t '$share/q_comp/q_comp'
```

Публикация трёх сообщений:

```bash
mosquitto_pub -h localhost -p 1883 -q 1 -t q_comp -m "q1"
mosquitto_pub -h localhost -p 1883 -q 1 -t q_comp -m "q2"
mosquitto_pub -h localhost -p 1883 -q 1 -t q_comp -m "q3"
```

Ожидаемо: сумма сообщений у A и B = 3, без дублей. Пример распределения: `A=q1,q3`, `B=q2`.

**Как это работает внутри**:

- SUBSCRIBE с `$share/...` → `BrokerFacade.subscribe()` парсит shared-фильтр, добавляет в `ClientSession.subscribedQueues`
- PUBLISH в destination, который есть в `destinations` как `QUEUE`:
  - `QueueService.enqueue()` кладёт сообщение в `QueueStore.readyMessages` и `messages.status=READY`
- `DeliveryService.deliverNextQueueMessage()`:
  - `QueueStore.claim()` атомарно достаёт одно сообщение в `inflightMessages` и `messages.status=INFLIGHT`
  - выбирает потребителя round-robin
  - отправляет ему `PUBLISH`
- Потребитель отвечает `PUBACK`:
  - `BrokerFacade.acknowledgeDelivery()` удаляет из `inflight`, ставит `messages.status=ACKED`

### 5.3 QoS и ACK

- Все доставки подписчикам идут с QoS 1.
- Publisher при QoS 1 получает от брокера PUBACK → у него гарантия “принято брокером”.
- Подписчик PUBACK’ом говорит брокеру “обработал” → у брокера гарантия “доставлено потребителю”.

---

## 6. Персистентность (PostgreSQL + eager restore)

### 6.1 Что такое eager restore

При старте `broker` выполняется:

- `TopicService.@PostConstruct restoreFromDatabase()`
- `QueueService.@PostConstruct restoreFromDatabase()`

Эти методы читают `destinations` и `messages` и восстанавливают in-memory структуры (`TopicStore`, `QueueStore`) из БД.

### 6.2 Тест “сохранение + восстановление”

1. Создать очередь:

```bash
curl -s -X POST http://localhost:8080/api/queues \
  -H 'Content-Type: application/json' \
  -d '{"name":"q_persist","mode":"FIFO"}'
```

1. Опубликовать сообщение **без подписчиков**:

```bash
mosquitto_pub -h localhost -p 1883 -q 1 -t q_persist -m "persist_msg"
```

1. Перезапустить брокер:

```bash
docker compose restart broker
```

1. Подписаться:

```bash
mosquitto_sub -h localhost -p 1883 -q 1 -i c_persist -t '$share/q_persist/q_persist'
```

Ожидаемо: `persist_msg` приходит.

В логах:

```
Restored queues from DB: N
```

---

## 7. At-least-once (гарантия доставки при крэше)

Это **самый важный тест** 2 этапа.

### 7.1 Суть

- Сообщение попало в `INFLIGHT` (послано подписчику), но брокер упал до PUBACK.
- После старта: `QueueService.restoreFromDatabase()` видит `INFLIGHT` и переводит обратно в `READY`.
- Сообщение **повторно доставится** новому (или тому же) потребителю.

### 7.2 Процедура теста

```bash
# 1. Создать очередь
Q=q_inflight_test
curl -s -X POST http://localhost:8080/api/queues \
  -H 'Content-Type: application/json' \
  -d "{\"name\":\"$Q\",\"mode\":\"FIFO\"}"

# 2. Опубликовать (пока без consumer)
mosquitto_pub -h localhost -p 1883 -q 1 -t "$Q" -m "inflight_msg"

# 3. Проверить в БД статус READY
docker exec pigeonmq-postgres psql -U pigeonmq -d pigeonmq \
  -c "select destination_name,status from messages where destination_name='$Q';"

# 4. Запустить consumer'а
mosquitto_sub -h localhost -p 1883 -q 1 -i cX -t "\$share/$Q/$Q"
# (как только увидите сообщение — СРАЗУ выполните крэш в другом окне)

# 5. Крэш брокера (имитация аварии)
docker compose kill broker
docker compose up -d broker

# 6. Проверить в БД: статус должен стать READY
docker exec pigeonmq-postgres psql -U pigeonmq -d pigeonmq \
  -c "select destination_name,status from messages where destination_name='$Q';"

# 7. Запустить consumer заново:
mosquitto_sub -h localhost -p 1883 -q 1 -i cY -t "\$share/$Q/$Q"
```

Ожидаемо:

- в БД до крэша: `INFLIGHT`
- после рестарта: `READY`
- consumer получает `inflight_msg` **ещё раз** — это и есть at-least-once

### 7.3 Если всё ACKнулось до крэша

Тогда в БД статус станет `ACKED`, и сообщение **не будет** доставлено повторно — это тоже корректно.

---

## 8. Восстановление offset’ов для топиков

Для топиков offset сохраняется по `clientId` в таблице `consumer_offsets`. Это нужно, чтобы после рестарта клиент **не получал заново** те же самые старые сообщения.

### Тест

```bash
# 1. Подписчик c фиксированным clientId
mosquitto_sub -h localhost -p 1883 -q 1 -i sub_offset -t t_offsets &

# 2. Публикация
mosquitto_pub -h localhost -p 1883 -q 1 -t t_offsets -m "o1"
mosquitto_pub -h localhost -p 1883 -q 1 -t t_offsets -m "o2"

# 3. Отключаем подписчика (Ctrl+C в его окне)

# 4. Рестарт брокера
docker compose restart broker

# 5. Переподключаем с ТЕМ ЖЕ clientId
mosquitto_sub -h localhost -p 1883 -q 1 -i sub_offset -t t_offsets
```

Ожидаемо: `o1` и `o2` **не должны** прийти повторно.
Проверка в БД:

```sql
select client_id, topic_name, sent_offset
from consumer_offsets
where client_id='sub_offset';
```

`sent_offset` должен быть равен числу уже доставленных сообщений.

---

## 9. SQL-инспекция БД

Подключиться:

```bash
docker exec -it pigeonmq-postgres psql -U pigeonmq -d pigeonmq
```

Полезные запросы:

```sql
-- Какие топики/очереди существуют
select name, type, mode, created_at from destinations order by created_at;

-- Что лежит в сообщениях (сводка)
select destination_name, dest_type, status, count(*)
from messages
group by 1,2,3
order by 1;

-- Последние сообщения
select id, destination_name, status, offset_val, created_at
from messages
order by created_at desc
limit 20;

-- Offsets подписчиков (для топиков)
select * from consumer_offsets order by client_id, topic_name;
```

### Значение статусов в `messages`

- **READY** — лежит в очереди/топике, ждёт отправки потребителю.
- **INFLIGHT** — отправлено потребителю, ждём PUBACK.
- **ACKED** — потребитель подтвердил приём (для очередей — финал).

Для топиков основное — запись `READY` и `consumer_offsets`. Для очередей — переход `READY → INFLIGHT → ACKED` и перевод `INFLIGHT → READY` при старте.

---

## 10. Архитектура: что за что отвечает

### 10.1 Транспорт MQTT (Netty)

Файлы: `transport/mqtt/*`.

- `**MqttTransport`** — запускает Netty сервер на 1883, управляется Spring’ом как `SmartLifecycle`.
- `**MqttChannelInitializer**` — pipeline для каждого нового соединения:
  - `IdleStateHandler` — отключает клиента при бездействии
  - `MqttDecoder` — байты → `MqttMessage`
  - `MqttEncoder` — `MqttMessage` → байты
  - `MqttPacketHandler` — наш обработчик
- `**MqttPacketHandler**` — роутит по типу пакета:
  - `CONNECT` → `brokerFacade.registerClient()`
  - `SUBSCRIBE` → `brokerFacade.subscribe()`
  - `PUBLISH` → `brokerFacade.publish()`; если QoS 1 — отвечает PUBACK publisher’у
  - `PUBACK` (от подписчика) → `brokerFacade.acknowledgeDelivery()`
  - `PINGREQ` / `DISCONNECT` / отключение канала — обрабатываются отдельно

### 10.2 Доменный слой (in-memory)

Файлы: `domain/*`.

- `**Message**` (record) — доменное сообщение с `id, destination, destType, payload, priority, offset, createdAt`.
- `**TopicStore**` — offset-лог топика:
  - `ConcurrentSkipListMap<Long, Message>`
  - `publish()` увеличивает offset, `getMessagesFrom(fromOffset)` — чтение хвоста
  - `restore()` — восстановление из БД
- `**QueueStore**` — очередь:
  - `readyMessages` — FIFO (`ConcurrentLinkedQueue`) или PRIORITY (`PriorityBlockingQueue`)
  - `inflightMessages` — `ConcurrentHashMap<UUID, Message>` (ожидают PUBACK)
  - `enqueue()`, `claim()`, `ack()`, `nack()`, `restoreReady()`
- `**ClientSession**` — runtime-состояние клиента:
  - `Channel`, `subscribedTopics`, `subscribedQueues`
  - `topicSentOffsets` — докуда этому клиенту уже доставили для каждого топика
  - `pendingDeliveries` — что было отправлено и ждёт PUBACK
  - `nextPacketId()` — счётчик 1..65535 для MQTT packet id
- `**DestinationType**`: `TOPIC | QUEUE`
- `**QueueMode**`: `FIFO | PRIORITY`
- `**PendingDelivery**`: метаданные “что послано клиенту”

### 10.3 Сервисы

Файлы: `service/*`.

- `**TopicService**` — управление топиками:
  - реестр `ConcurrentMap<String, TopicStore>`
  - `create()`, `delete()`, `publish()`, `getAll()`, `exists()`
  - `@PostConstruct restoreFromDatabase()` — eager restore
  - сохраняет сообщения в `messages` со `status=READY`
- `**QueueService**` — управление очередями:
  - реестр `ConcurrentMap<String, QueueStore>`
  - `create()`, `delete()`, `enqueue()`
  - `@PostConstruct restoreFromDatabase()` — восстанавливает очереди и переводит `INFLIGHT→READY`
- `**SessionService**` — реестр активных клиентов:
  - `register()`, `remove()`, `findByChannel()`, `getActiveSessions()`
  - при подключении загружает `consumer_offsets` клиента в `ClientSession.topicSentOffsets`
- `**DeliveryService**` — сердце доставки:
  - `@PostConstruct` создаёт `ScheduledExecutorService`, раз в `delivery-interval-ms` выполняет `deliveryLoop()`
  - `deliverTopicMessages(name)` — fan-out для топика: каждому подписчику отдаёт сообщения, начиная с его offset
  - `deliverQueueMessages(name)` — для очереди берёт одно сообщение, round-robin подписчика; **в транзакции** переводит статус в `INFLIGHT`
  - `deliveryLoop()` — периодическая зачистка: проходит все топики/очереди и шлёт то, что не ушло немедленно
- `**BrokerFacade`** — единая точка входа для MQTT handler’а:
  - `registerClient()`, `removeClient()`, `publish()`, `subscribe()`, `unsubscribe()`, `acknowledgeDelivery()`
  - `resolveDestinationType(destination)`:
    1. смотрит `destinations` в БД (источник правды)
    2. если нет записи — использует in-memory проверку
  - `parseTopicFilter()` — выделяет `$share/<group>/<dest>` для очередей

### 10.4 Персистентность

Файлы: `persistence/entity/*`, `persistence/repository/*`, `resources/db/migration/V1__init.sql`.

Таблицы:

- `**destinations**` — справочник топиков/очередей (`name`, `type`, `mode`, `created_at`).
- `**messages**` — все сообщения (`id, destination_name, dest_type, payload, priority, offset_val, status, created_at`).
- `**consumer_offsets**` — сохранённые позиции подписчиков для топиков (`client_id, topic_name, sent_offset`).

Сущности:

- `DestinationEntity`, `MessageEntity`, `MessageStatus`, `ConsumerOffsetEntity(+Id)`.

Репозитории (Spring Data JPA):

- `DestinationRepository` — CRUD destinations
- `MessageRepository` — поиск по `destination+status`, `updateStatus(id, status)` (транзакционный UPDATE)
- `ConsumerOffsetRepository` — чтение/запись offsets по `clientId`

Миграция `V1__init.sql` создаёт таблицы + индексы:

- `idx_messages_destination_status_offset` — для быстрой выборки при восстановлении и доставке
- `idx_messages_status`

### 10.5 Конфигурация

- `**config/BrokerProperties**` (`@ConfigurationProperties("pigeonmq")`):
  - `mqttPort`, `deliveryIntervalMs`, `ackTimeoutSeconds`, `maxRetries`, `workerPoolSize`
- `**resources/application.yml**`:
  - `server.port`, `pigeonmq.*`
  - `spring.datasource` (URL/user/password из env)
  - `spring.jpa.hibernate.ddl-auto: validate`
  - `spring.flyway.enabled: true`
- `**.env**` — переменные окружения (`POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB`, `PIGEONMQ_DB_URL` и т.д.)

### 10.6 Жизненный цикл запроса “Publish → Deliver”

Визуально шаги (для очереди):

1. Publisher подключается (CONNECT) → `SessionService.register()`
2. Publisher отправляет PUBLISH `q_example` QoS 1
3. `MqttPacketHandler.handlePublish()` → `BrokerFacade.publish()`
4. `resolveDestinationType("q_example")` — смотрит в БД `destinations`, находит `QUEUE`
5. `QueueService.enqueue()` → `QueueStore.readyMessages.offer()` + INSERT `messages status=READY`
6. `DeliveryService.deliverQueueMessages("q_example")`:
  - в транзакции: `QueueStore.claim()` + `messageRepository.updateStatus(id, INFLIGHT)`
  - выбирает подписчика round-robin
  - отправляет PUBLISH ему
  - запоминает `PendingDelivery` в `ClientSession`
7. Consumer получает PUBLISH, шлёт PUBACK
8. `MqttPacketHandler.handlePuback()` → `BrokerFacade.acknowledgeDelivery()`:
  - `ClientSession.completeDelivery(packetId)` → находит `PendingDelivery`
  - `QueueStore.ack(messageId)` — убирает из inflight
  - `messageRepository.updateStatus(id, ACKED)`

### 10.7 Жизненный цикл при перезапуске

1. Контекст Spring стартует
2. Flyway применяет миграции
3. `TopicService.@PostConstruct` восстанавливает топики и их сообщения
4. `QueueService.@PostConstruct` восстанавливает очереди:
  - сообщения `READY` и `INFLIGHT` кладёт в `readyMessages`
  - у `INFLIGHT` статус в БД переводит обратно в `READY`
5. `DeliveryService.@PostConstruct` запускает delivery scheduler
6. `MqttTransport.start()` открывает порт 1883
7. Клиенты переподключаются → `SessionService.register()` восстанавливает offsets из `consumer_offsets` → доставка продолжается

---

## 11. Итоговый чек-лист тестирования

Минимальный:

- `docker compose up -d --build broker` — оба контейнера Up
- `GET /health` → 200
- создать топик через REST, удалить через REST
- создать очередь FIFO и PRIORITY
- topic fan-out (2 подписчика получают одно сообщение)
- queue competing consumers (3 сообщения, 2 consumer’а, без дублей)
- persistence restart: publish в очередь → restart → consume получает

Расширенный (2 этап):

- at-least-once: publish → kill broker → restart → в БД `READY` → consume получает сообщение
- topic offsets: тот же `clientId` после рестарта не получает повтор старых сообщений
- SQL-проверки: `destinations`, `messages` статусы, `consumer_offsets`

Если все пункты проходят — требования **Стадии 2 (персистентность, восстановление, at-least-once)** выполнены.