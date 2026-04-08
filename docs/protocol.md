# Протокол pigeonMQ поверх MQTT

## 0) Какой MQTT используем

Для проекта рекомендуем **MQTT 5.0**:

- shared subscriptions для “очередей” (competing consumers)
- message properties / user properties для TTL, headers и приоритетов

На практике на MVP можно начинать с subset **MQTT 3.1.1** (там нет shared subscriptions), но тогда “очередь” придётся реализовывать на уровне broker’а (не через shared subscriptions).

## 1) Модель destination в pigeonMQ

Топик и очередь в pigeonMQ маппятся на MQTT так:

### Topic (Pub/Sub, fan-out по subscribers)

- `destination_type = TOPIC`
- subscriber подписывается на обычный MQTT topic filter:
  - `orders` (если destination = `orders`)

### Queue (point-to-point, competing consumers)

- `destination_type = QUEUE`
- subscriber подписывается на **shared subscription**:
  - `$share/<queue_group>/tasks`
- publish при этом делается в обычный MQTT topic:
  - `tasks`

Гарантия “одно сообщение одному consumer’у” достигается тем, что shared subscription распределяет сообщения между участниками группы.

> В pigeonMQ FIFO/priority порядок на queue реализуется на уровне delivery/storage engine (по данным в PostgreSQL), а не средствами MQTT.

## 2) At-least-once и ACK/NACK

MQTT сам по себе может дать “at-least-once delivery” на уровне протокола за счёт **QoS**:

- QoS 1: broker повторно отправит, если PUBACK не дошёл
- QoS 2: строгее, но сложнее

Но MQTT ACK/PUBACK — это “сообщение получено клиентом”, а не “бизнес-обработка завершена”.

Поэтому в pigeonMQ предусмотрено два варианта:

### Вариант A (MVP, проще)

- использовать MQTT **QoS 1**
- считать `PUBACK` эквивалентом `ACK` для delivery-state
- NACK как “не ACKить” (клиент просто не подтверждает, либо обработка завершается без вызова ack в вашем SDK)

Это даёт at-least-once в учебных сценариях и достаточно для Этапов 2-6.

### Вариант B (ближе к требованиям задания)

- сделать **application-level ACK** отдельным MQTT publish на ack topic
- пример: `pigeonmq.ack/<client_id>`
- payload: `{ message_id, destination, dest_type, status }`
- broker обновляет `consumer_offset` / `queue_delivery_state` только после application ACK

NACK в этом случае:

- клиент публикует `{status: "NACK"}` или не публикует ACK до `ack_deadline`
- broker переводит сообщение в READY (retry) или в DLQ

## 3) TTL и headers

Рекомендуемый перенос в MQTT:

### TTL

- MQTT 5 user property: `x-ttl-seconds`
- при delivery broker вычисляет `expires_at`/`now()` и не отправляет просроченные сообщения

или (если делаете full MQTT 5 properties):

- использовать `Message Expiry Interval` (message-expiry-interval)

### Headers

- MQTT 5 user properties (ключ-значение) `x-h-<name>: <value>`

## 4) Priority

MQTT не имеет встроенной “priority queue” семантики.

Поэтому priority хранится в PostgreSQL (поле `priority`/user property `x-priority`) и используется broker’ом при выборе следующего сообщения для claim:

- очередь FIFO: меньше `offset` раньше
- очередь PRIORITY: больше priority раньше (при равенстве — FIFO)

## 5) Сценарии на уровне flow

### Publish

1. Клиент publisher делает `CONNECT`
2. Клиент публикует `PUBLISH` на MQTT topic:
  - `orders` для TOPIC
  - `tasks` для QUEUE
  - QoS 1
  - user properties: ttl/priority/headers + payload
3. MQTT broker (ваш сервер) принимает сообщение, пишет в PostgreSQL
4. Delivery engine доставляет подписчикам (делает outbound publish на topic filters)
5. Broker отвечает PUBACK publisher’у (если QoS 1)

### Subscribe / Delivery

1. Subscriber делает `CONNECT`
2. Subscriber подписывается:
  - topic: `orders`
  - queue: `$share/tasks/tasks`
3. Broker отправляет сообщения (outbound `PUBLISH`, QoS 1)
4. Дальше:
  - MVP: broker считает PUBACK клиента = ACK
  - вариант B: broker ждёт application ACK

## 6) Почему мы не “переиспользуем” старый custom TCP протокол

Старый custom TCP протокол требует framing/ACK/NACK поверх TCP и отдельной реализации wire-level.

MQTT вместо этого:

- даёт готовую транспортную часть (CONNECT/SUBSCRIBE/PUBLISH/PUBACK)
- оставляет вам “своя брокерная” часть: storage/delivery/queue semantics/at-least-once tracking в PostgreSQL