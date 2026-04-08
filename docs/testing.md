# Тестирование pigeonMQ

Полное руководство по проверке работоспособности всех компонентов брокера.

---

## Содержание

1. [Подготовка](#1-подготовка)
2. [Swagger UI](#2-swagger-ui)
3. [REST API — Health](#3-rest-api--health)
4. [REST API — Topics CRUD](#4-rest-api--topics-crud)
5. [REST API — Queues CRUD](#5-rest-api--queues-crud)
6. [REST API — Валидация и ошибки](#6-rest-api--валидация-и-ошибки)
7. [MQTT — Topic (fan-out)](#7-mqtt--topic-fan-out)
8. [MQTT — Queue (competing consumers)](#8-mqtt--queue-competing-consumers)
9. [Комплексный сценарий: REST + MQTT](#9-комплексный-сценарий-rest--mqtt)
10. [IntelliJ HTTP Client](#10-intellij-http-client)
11. [Чек-лист](#11-чек-лист)

---

## 1. Подготовка

### Зависимости


| Инструмент        | Назначение                  | Установка (macOS)                      |
| ----------------- | --------------------------- | -------------------------------------- |
| Java 17+          | Запуск приложения           | `brew install openjdk@17`              |
| Maven 3.9+        | Сборка                      | `brew install maven`                   |
| mosquitto-clients | MQTT-клиент (pub/sub)       | `brew install mosquitto`               |
| curl              | HTTP-запросы из терминала   | встроен в macOS                        |
| IntelliJ IDEA     | HTTP Client, запуск проекта | [jetbrains.com](https://jetbrains.com) |


### Сборка и запуск

```bash
# Сборка
mvn clean package -DskipTests

# Запуск
java -jar target/pigeonmq-0.1.0-SNAPSHOT.jar
```

После запуска в логах должно появиться:

```
Started PigeonMQApplication in X.XXX seconds
MQTT transport listening on port 1883
Delivery scheduler started (interval=100ms, workers=4)
```

Это означает, что работают оба транспорта:

- **HTTP (REST API)** — порт `8080`
- **MQTT** — порт `1883`

---

## 2. Swagger UI

Открыть в браузере:

```
http://localhost:8080/swagger-ui
```

Swagger UI позволяет:

- Просмотреть все доступные эндпоинты
- Отправлять запросы прямо из интерфейса (кнопка **Try it out**)
- Видеть схемы DTO (запросы и ответы)

OpenAPI-спецификация в JSON:

```
http://localhost:8080/api-docs
```

---

## 3. REST API — Health

### Тест: проверка доступности брокера

```bash
curl -s http://localhost:8080/health | python3 -m json.tool
```

**Ожидаемый ответ** (`200 OK`):

```json
{
    "status": "UP",
    "topicCount": 0,
    "queueCount": 0,
    "activeConnections": 0
}
```


| Поле                | Описание                           |
| ------------------- | ---------------------------------- |
| `status`            | Всегда `"UP"` если брокер работает |
| `topicCount`        | Количество созданных топиков       |
| `queueCount`        | Количество созданных очередей      |
| `activeConnections` | Активные MQTT-подключения          |


---

## 4. REST API — Topics CRUD

### 4.1. Создание топика

```bash
curl -s -X POST http://localhost:8080/api/topics \
  -H 'Content-Type: application/json' \
  -d '{"name":"orders"}'
```

**Ожидаемый ответ** (`201 Created`):

```json
{"success": true, "name": "orders", "message": "Created successfully"}
```

### 4.2. Список топиков

```bash
curl -s http://localhost:8080/api/topics | python3 -m json.tool
```

**Ожидаемый ответ** (`200 OK`):

```json
[
    {
        "name": "orders",
        "messageCount": 0,
        "latestOffset": 0,
        "createdAt": "2026-04-08T09:47:25.355044Z"
    }
]
```

### 4.3. Детали топика

```bash
curl -s http://localhost:8080/api/topics/orders | python3 -m json.tool
```

**Ожидаемый ответ** (`200 OK`):

```json
{
    "name": "orders",
    "messageCount": 0,
    "latestOffset": 0,
    "createdAt": "2026-04-08T09:47:25.355044Z"
}
```

### 4.4. Удаление топика

```bash
curl -s -X DELETE http://localhost:8080/api/topics/orders
```

**Ожидаемый ответ** (`200 OK`):

```json
{"success": true, "name": "orders", "message": "Deleted successfully"}
```

### 4.5. Проверка удаления

```bash
curl -s http://localhost:8080/api/topics
```

**Ожидаемый ответ**: `[]`

---

## 5. REST API — Queues CRUD

### 5.1. Создание FIFO-очереди

```bash
curl -s -X POST http://localhost:8080/api/queues \
  -H 'Content-Type: application/json' \
  -d '{"name":"emails","mode":"FIFO"}'
```

**Ожидаемый ответ** (`201 Created`):

```json
{"success": true, "name": "emails", "message": "Created successfully"}
```

### 5.2. Создание PRIORITY-очереди

```bash
curl -s -X POST http://localhost:8080/api/queues \
  -H 'Content-Type: application/json' \
  -d '{"name":"tasks","mode":"PRIORITY"}'
```

### 5.3. Список очередей

```bash
curl -s http://localhost:8080/api/queues | python3 -m json.tool
```

**Ожидаемый ответ** (`200 OK`):

```json
[
    {
        "name": "emails",
        "mode": "FIFO",
        "readyCount": 0,
        "inflightCount": 0,
        "createdAt": "2026-04-08T..."
    },
    {
        "name": "tasks",
        "mode": "PRIORITY",
        "readyCount": 0,
        "inflightCount": 0,
        "createdAt": "2026-04-08T..."
    }
]
```

### 5.4. Удаление очереди

```bash
curl -s -X DELETE http://localhost:8080/api/queues/tasks
```

---

## 6. REST API — Валидация и ошибки

Все ошибки возвращаются в едином формате:

```json
{
    "timestamp": "2026-04-08T09:47:42.060105Z",
    "status": 404,
    "error": "Not Found",
    "message": "Topic not found: nonexistent"
}
```

### 6.1. Запрос несуществующего ресурса → `404`

```bash
curl -s http://localhost:8080/api/topics/nonexistent
```

**Ожидаемый ответ** (`404 Not Found`):

```json
{
    "timestamp": "...",
    "status": 404,
    "error": "Not Found",
    "message": "Topic not found: nonexistent"
}
```

### 6.2. Пустое имя топика → `400`

```bash
curl -s -X POST http://localhost:8080/api/topics \
  -H 'Content-Type: application/json' \
  -d '{"name":""}'
```

**Ожидаемый ответ** (`400 Bad Request`):

```json
{
    "timestamp": "...",
    "status": 400,
    "error": "Bad Request",
    "message": "name: Topic name is required"
}
```

### 6.3. Некорректный режим очереди → `400`

```bash
curl -s -X POST http://localhost:8080/api/queues \
  -H 'Content-Type: application/json' \
  -d '{"name":"test","mode":"LIFO"}'
```

**Ожидаемый ответ** (`400 Bad Request`):

```json
{
    "timestamp": "...",
    "status": 400,
    "error": "Bad Request",
    "message": "Invalid queue mode: LIFO. Allowed: FIFO, PRIORITY"
}
```

### 6.4. Удаление несуществующей очереди → `404`

```bash
curl -s -X DELETE http://localhost:8080/api/queues/nonexistent
```

**Ожидаемый ответ** (`404 Not Found`):

```json
{
    "timestamp": "...",
    "status": 404,
    "error": "Not Found",
    "message": "Queue not found: nonexistent"
}
```

---

## 7. MQTT — Topic (fan-out)

**Принцип**: каждый подписчик получает **все** сообщения.

### Тест: 2 подписчика на один топик

**Терминал 1** — подписчик 1:

```bash
mosquitto_sub -h localhost -p 1883 -t "orders" -q 1 -i sub1
```

**Терминал 2** — подписчик 2:

```bash
mosquitto_sub -h localhost -p 1883 -t "orders" -q 1 -i sub2
```

**Терминал 3** — публикация:

```bash
mosquitto_pub -h localhost -p 1883 -t "orders" -q 1 -m '{"item":"book","qty":1}'
mosquitto_pub -h localhost -p 1883 -t "orders" -q 1 -m '{"item":"laptop","qty":3}'
```

**Ожидаемый результат**: оба подписчика получают **оба** сообщения:

```
sub1: {"item":"book","qty":1}
sub1: {"item":"laptop","qty":3}

sub2: {"item":"book","qty":1}
sub2: {"item":"laptop","qty":3}
```

### Однострочный тест (автоматический)

```bash
mosquitto_sub -h localhost -p 1883 -t "orders" -q 1 -i sub1 -C 2 > /tmp/sub1.txt &
mosquitto_sub -h localhost -p 1883 -t "orders" -q 1 -i sub2 -C 2 > /tmp/sub2.txt &
sleep 1
mosquitto_pub -h localhost -p 1883 -t "orders" -q 1 -m 'msg-1'
mosquitto_pub -h localhost -p 1883 -t "orders" -q 1 -m 'msg-2'
sleep 2
echo "=== Sub1 ===" && cat /tmp/sub1.txt
echo "=== Sub2 ===" && cat /tmp/sub2.txt
```


| Параметр | Значение                          |
| -------- | --------------------------------- |
| `-t`     | Имя топика                        |
| `-q 1`   | QoS 1 (at-least-once)             |
| `-i`     | Client ID (обязателен, уникален)  |
| `-C 2`   | Принять 2 сообщения и завершиться |


---

## 8. MQTT — Queue (competing consumers)

**Принцип**: каждое сообщение получает **только один** подписчик (round-robin).

Очереди реализованы через **MQTT Shared Subscriptions** — формат топика:

```
$share/<group>/<queue_name>
```

### Подготовка: создать очередь через REST

```bash
curl -s -X POST http://localhost:8080/api/queues \
  -H 'Content-Type: application/json' \
  -d '{"name":"emails","mode":"FIFO"}'
```

### Тест: 2 competing consumers

**Терминал 1** — потребитель 1:

```bash
mosquitto_sub -h localhost -p 1883 -t '$share/g1/emails' -q 1 -i worker1
```

**Терминал 2** — потребитель 2:

```bash
mosquitto_sub -h localhost -p 1883 -t '$share/g1/emails' -q 1 -i worker2
```

**Терминал 3** — публикация 4 сообщений:

```bash
mosquitto_pub -h localhost -p 1883 -t "emails" -q 1 -m "email-1"
mosquitto_pub -h localhost -p 1883 -t "emails" -q 1 -m "email-2"
mosquitto_pub -h localhost -p 1883 -t "emails" -q 1 -m "email-3"
mosquitto_pub -h localhost -p 1883 -t "emails" -q 1 -m "email-4"
```

**Ожидаемый результат**: сообщения распределены между потребителями (round-robin):

```
worker1: email-1
worker1: email-3

worker2: email-2
worker2: email-4
```

> Точное распределение может отличаться, но каждое сообщение получает **ровно один** потребитель.

### Однострочный тест (автоматический)

```bash
mosquitto_sub -h localhost -p 1883 -t '$share/g1/emails' -q 1 -i w1 -C 2 > /tmp/w1.txt &
mosquitto_sub -h localhost -p 1883 -t '$share/g1/emails' -q 1 -i w2 -C 2 > /tmp/w2.txt &
sleep 1
for i in 1 2 3 4; do
  mosquitto_pub -h localhost -p 1883 -t "emails" -q 1 -m "email-$i"
  sleep 0.2
done
sleep 2
echo "=== Worker1 ===" && cat /tmp/w1.txt
echo "=== Worker2 ===" && cat /tmp/w2.txt
```

---

## 9. Комплексный сценарий: REST + MQTT

Полный цикл: создание ресурса → публикация → проверка состояния.

### Шаг 1. Создать топик через REST

```bash
curl -s -X POST http://localhost:8080/api/topics \
  -H 'Content-Type: application/json' \
  -d '{"name":"events"}'
```

### Шаг 2. Подписаться через MQTT

```bash
mosquitto_sub -h localhost -p 1883 -t "events" -q 1 -i listener -C 3 &
sleep 1
```

### Шаг 3. Опубликовать сообщения через MQTT

```bash
mosquitto_pub -h localhost -p 1883 -t "events" -q 1 -m '{"type":"user_created","id":1}'
mosquitto_pub -h localhost -p 1883 -t "events" -q 1 -m '{"type":"order_placed","id":42}'
mosquitto_pub -h localhost -p 1883 -t "events" -q 1 -m '{"type":"payment_received","id":42}'
```

### Шаг 4. Проверить состояние через REST

```bash
curl -s http://localhost:8080/api/topics/events | python3 -m json.tool
```

**Ожидаемый ответ**:

```json
{
    "name": "events",
    "messageCount": 3,
    "latestOffset": 3,
    "createdAt": "..."
}
```

### Шаг 5. Проверить health

```bash
curl -s http://localhost:8080/health | python3 -m json.tool
```

**Ожидаемый ответ**: `topicCount >= 1`, `activeConnections >= 0` (подписчик мог уже отключиться после `-C 3`).

---

## 10. IntelliJ HTTP Client

В проекте есть файл `requests/pigeonmq.http`, который можно открыть в IntelliJ IDEA.

### Как использовать

1. Открыть файл `requests/pigeonmq.http` в IntelliJ
2. Убедиться, что приложение запущено на `localhost:8080`
3. Нажать зелёную стрелку ▶ рядом с нужным запросом
4. Результат отобразится в панели **Run** внизу

### Доступные запросы


| Запрос                   | Метод    | URL                  |
| ------------------------ | -------- | -------------------- |
| Health check             | `GET`    | `/health`            |
| Создать топик            | `POST`   | `/api/topics`        |
| Список топиков           | `GET`    | `/api/topics`        |
| Детали топика            | `GET`    | `/api/topics/orders` |
| Удалить топик            | `DELETE` | `/api/topics/orders` |
| Создать FIFO-очередь     | `POST`   | `/api/queues`        |
| Создать PRIORITY-очередь | `POST`   | `/api/queues`        |
| Список очередей          | `GET`    | `/api/queues`        |
| Детали очереди           | `GET`    | `/api/queues/tasks`  |
| Удалить очередь          | `DELETE` | `/api/queues/tasks`  |


---

## 11. Чек-лист


| #   | Тест                                              | Способ      | Ожидаемый результат                          |
| --- | ------------------------------------------------- | ----------- | -------------------------------------------- |
| 1   | Приложение запускается без ошибок                 | Terminal    | Лог: `Started PigeonMQApplication`           |
| 2   | MQTT-сервер слушает порт 1883                     | Terminal    | Лог: `MQTT transport listening on port 1883` |
| 3   | Health check возвращает `200`                     | curl / HTTP | `{"status":"UP",...}`                        |
| 4   | Swagger UI доступен                               | Браузер     | Страница на `/swagger-ui`                    |
| 5   | OpenAPI spec доступна                             | curl        | JSON на `/api-docs`                          |
| 6   | Создание топика → `201`                           | curl / HTTP | `{"success":true,...}`                       |
| 7   | Список топиков → `200`                            | curl / HTTP | Массив топиков                               |
| 8   | Детали топика → `200`                             | curl / HTTP | Объект с `name`, `messageCount`, ...         |
| 9   | Удаление топика → `200`                           | curl / HTTP | `{"success":true,...}`                       |
| 10  | Создание FIFO-очереди → `201`                     | curl / HTTP | `{"success":true,...}`                       |
| 11  | Создание PRIORITY-очереди → `201`                 | curl / HTTP | `{"success":true,...}`                       |
| 12  | Список очередей → `200`                           | curl / HTTP | Массив очередей                              |
| 13  | Удаление очереди → `200`                          | curl / HTTP | `{"success":true,...}`                       |
| 14  | Запрос несуществующего топика → `404`             | curl / HTTP | Единый формат ошибки                         |
| 15  | Пустое имя → `400`                                | curl / HTTP | `"name: Topic name is required"`             |
| 16  | Невалидный режим очереди → `400`                  | curl / HTTP | `"Invalid queue mode: ..."`                  |
| 17  | MQTT: topic fan-out (2 подписчика)                | mosquitto   | Оба получают все сообщения                   |
| 18  | MQTT: queue competing consumers (2 потребителя)   | mosquitto   | Каждое сообщение — ровно одному              |
| 19  | MQTT: QoS 1 (at-least-once)                       | mosquitto   | Сообщения не теряются                        |
| 20  | REST + MQTT: создать → подписаться → опубликовать | curl + mqtt | `messageCount` растёт                        |
| 21  | Health: `activeConnections` обновляется           | curl        | Число > 0 при подключённых клиентах          |
| 22  | Health: `topicCount` / `queueCount` обновляются   | curl        | Числа соответствуют созданным                |


