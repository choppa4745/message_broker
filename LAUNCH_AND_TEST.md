# pigeonMQ — Полная инструкция по запуску и тестированию

---

## Содержание

1. [Требования](#1-требования)
2. [Сборка проекта](#2-сборка-проекта)
3. [Запуск — локально (без Docker)](#3-запуск--локально-без-docker)
4. [Запуск — через Docker Compose](#4-запуск--через-docker-compose)
5. [Проверка успешного запуска](#5-проверка-успешного-запуска)
6. [Swagger UI — визуальное тестирование](#6-swagger-ui--визуальное-тестирование)
7. [Сценарий 1: Health Check](#7-сценарий-1-health-check)
8. [Сценарий 2: CRUD топиков (REST)](#8-сценарий-2-crud-топиков-rest)
9. [Сценарий 3: CRUD очередей (REST)](#9-сценарий-3-crud-очередей-rest)
10. [Сценарий 4: Валидация и обработка ошибок](#10-сценарий-4-валидация-и-обработка-ошибок)
11. [Сценарий 5: MQTT Topic — fan-out (все получают всё)](#11-сценарий-5-mqtt-topic--fan-out-все-получают-всё)
12. [Сценарий 6: MQTT Queue — competing consumers (по одному)](#12-сценарий-6-mqtt-queue--competing-consumers-по-одному)
13. [Сценарий 7: Комплексный — REST + MQTT вместе](#13-сценарий-7-комплексный--rest--mqtt-вместе)
14. [Сценарий 8: Множество подписчиков + нагрузка](#14-сценарий-8-множество-подписчиков--нагрузка)
15. [IntelliJ HTTP Client](#15-intellij-http-client)
16. [Остановка приложения](#16-остановка-приложения)
17. [Возможные проблемы](#17-возможные-проблемы)
18. [Итоговый чек-лист](#18-итоговый-чек-лист)

---

## 1. Требования

### Обязательное ПО

| Компонент           | Минимальная версия | Проверка                | Установка (macOS)           |
| ------------------- | ------------------ | ----------------------- | --------------------------- |
| Java (JDK)          | 17                 | `java -version`         | `brew install openjdk@17`   |
| Maven               | 3.9+               | `mvn -version`          | `brew install maven`        |
| mosquitto-clients   | любая              | `mosquitto_pub --help`  | `brew install mosquitto`    |
| curl                | любая              | `curl --version`        | встроен в macOS             |

### Опционально

| Компонент      | Назначение                       | Установка                            |
| -------------- | -------------------------------- | ------------------------------------ |
| Docker         | Контейнеризация                  | `brew install --cask docker`         |
| Docker Compose | Оркестрация контейнеров          | входит в Docker Desktop              |
| IntelliJ IDEA  | HTTP Client для `.http`-файлов   | [jetbrains.com](https://jetbrains.com) |

### Проверка всего за одну команду

```bash
java -version && mvn -version && mosquitto_pub --help 2>&1 | head -1 && echo "✅ Всё установлено"
```

---

## 2. Сборка проекта

```bash
cd /path/to/message_broker

# Полная сборка с тестами
mvn clean package

# Быстрая сборка (пропустить тесты)
mvn clean package -DskipTests
```

**Результат сборки**: `target/pigeonmq-0.1.0-SNAPSHOT.jar`

---

## 3. Запуск — локально (без Docker)

```bash
java -jar target/pigeonmq-0.1.0-SNAPSHOT.jar
```

### С кастомными параметрами

Через переменные окружения:

```bash
PIGEONMQ_MQTT_PORT=1883 PIGEONMQ_HTTP_PORT=9090 java -jar target/pigeonmq-0.1.0-SNAPSHOT.jar
```

Через аргументы Spring Boot:

```bash
java -jar target/pigeonmq-0.1.0-SNAPSHOT.jar \
  --server.port=9090 \
  --pigeonmq.mqtt-port=1884
```

### Ожидаемый вывод в консоли

```
  .   ____          _            __ _ _
 /\\ / ___'_ __ _ _(_)_ __  __ _ \ \ \ \
( ( )\___ | '_ | '_| | '_ \/ _` | \ \ \ \
 \\/  ___)| |_)| | | | | || (_| |  ) ) ) )
  '  |____| .__|_| |_|_| |_\__, | / / / /
 =========|_|==============|___/=/_/_/_/

...
Delivery scheduler started (interval=100ms, workers=4)
MQTT transport listening on port 1883
Started PigeonMQApplication in X.XXX seconds
```

Три ключевые строки:
1. `Delivery scheduler started` — фоновый цикл доставки запущен
2. `MQTT transport listening on port 1883` — Netty принимает MQTT-подключения
3. `Started PigeonMQApplication` — Spring Boot полностью инициализирован, HTTP на порту 8080

---

## 4. Запуск — через Docker Compose

```bash
# Скопировать .env из примера (один раз)
cp .env.example .env

# Собрать и запустить
docker compose up --build

# Или в фоне
docker compose up --build -d

# Посмотреть логи
docker compose logs -f broker
```

Это поднимает:
- **pigeonmq-broker** — порты `1883` (MQTT), `8080` (HTTP)
- **pigeonmq-postgres** — порт `5432` (для будущей персистентности)

---

## 5. Проверка успешного запуска

Выполните после запуска, чтобы убедиться что оба транспорта работают:

```bash
# HTTP (REST API)
curl -s http://localhost:8080/health

# MQTT (Netty)
mosquitto_pub -h localhost -p 1883 -t "ping" -m "test" -q 0
```

Если первая команда возвращает JSON с `"status":"UP"`, а вторая выполняется без ошибок — **всё работает**.

---

## 6. Swagger UI — визуальное тестирование

Откройте в браузере:

```
http://localhost:8080/swagger-ui
```

На этой странице:
- Все эндпоинты сгруппированы по тегам: **Topics**, **Queues**, **Health**
- Каждый эндпоинт можно раскрыть и нажать **Try it out**
- Swagger подставит формат запроса, вам нужно только заполнить данные и нажать **Execute**
- Ответ (тело, HTTP-код, заголовки) отображается прямо на странице

OpenAPI-спецификация в JSON (для импорта в Postman и т.д.):

```
http://localhost:8080/api-docs
```

---

## 7. Сценарий 1: Health Check

**Цель**: убедиться, что брокер запущен и отвечает.

```bash
curl -s http://localhost:8080/health | python3 -m json.tool
```

**Ожидаемый ответ** (HTTP `200`):

```json
{
    "status": "UP",
    "topicCount": 0,
    "queueCount": 0,
    "activeConnections": 0
}
```

| Поле                | Что означает                                    |
| ------------------- | ----------------------------------------------- |
| `status`            | `"UP"` — брокер работает                        |
| `topicCount`        | Сколько топиков создано                         |
| `queueCount`        | Сколько очередей создано                        |
| `activeConnections` | Сколько MQTT-клиентов подключено в данный момент |

---

## 8. Сценарий 2: CRUD топиков (REST)

### 8.1 Создать топик

```bash
curl -s -X POST http://localhost:8080/api/topics \
  -H 'Content-Type: application/json' \
  -d '{"name":"orders"}'
```

Ответ (`201 Created`):
```json
{"success": true, "name": "orders", "message": "Created successfully"}
```

### 8.2 Создать ещё один топик

```bash
curl -s -X POST http://localhost:8080/api/topics \
  -H 'Content-Type: application/json' \
  -d '{"name":"notifications"}'
```

### 8.3 Список всех топиков

```bash
curl -s http://localhost:8080/api/topics | python3 -m json.tool
```

Ответ (`200 OK`):
```json
[
    {
        "name": "orders",
        "messageCount": 0,
        "latestOffset": 0,
        "createdAt": "2026-04-08T..."
    },
    {
        "name": "notifications",
        "messageCount": 0,
        "latestOffset": 0,
        "createdAt": "2026-04-08T..."
    }
]
```

### 8.4 Получить конкретный топик

```bash
curl -s http://localhost:8080/api/topics/orders | python3 -m json.tool
```

### 8.5 Удалить топик

```bash
curl -s -X DELETE http://localhost:8080/api/topics/notifications
```

Ответ (`200 OK`):
```json
{"success": true, "name": "notifications", "message": "Deleted successfully"}
```

### 8.6 Убедиться в удалении

```bash
curl -s http://localhost:8080/api/topics | python3 -m json.tool
```

В массиве должен остаться только `orders`.

---

## 9. Сценарий 3: CRUD очередей (REST)

### 9.1 Создать FIFO-очередь

```bash
curl -s -X POST http://localhost:8080/api/queues \
  -H 'Content-Type: application/json' \
  -d '{"name":"emails","mode":"FIFO"}'
```

Ответ (`201 Created`):
```json
{"success": true, "name": "emails", "message": "Created successfully"}
```

### 9.2 Создать PRIORITY-очередь

```bash
curl -s -X POST http://localhost:8080/api/queues \
  -H 'Content-Type: application/json' \
  -d '{"name":"tasks","mode":"PRIORITY"}'
```

### 9.3 Список очередей

```bash
curl -s http://localhost:8080/api/queues | python3 -m json.tool
```

Ответ (`200 OK`):
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

### 9.4 Получить детали очереди

```bash
curl -s http://localhost:8080/api/queues/emails | python3 -m json.tool
```

### 9.5 Удалить очередь

```bash
curl -s -X DELETE http://localhost:8080/api/queues/tasks
```

---

## 10. Сценарий 4: Валидация и обработка ошибок

Все ошибки возвращаются в едином формате:

```json
{
    "timestamp": "2026-04-08T...",
    "status": <код>,
    "error": "<описание кода>",
    "message": "<что пошло не так>"
}
```

### 10.1 Запрос несуществующего топика → `404 Not Found`

```bash
curl -s http://localhost:8080/api/topics/nonexistent
```

```json
{
    "timestamp": "...",
    "status": 404,
    "error": "Not Found",
    "message": "Topic not found: nonexistent"
}
```

### 10.2 Пустое имя топика → `400 Bad Request`

```bash
curl -s -X POST http://localhost:8080/api/topics \
  -H 'Content-Type: application/json' \
  -d '{"name":""}'
```

```json
{
    "timestamp": "...",
    "status": 400,
    "error": "Bad Request",
    "message": "name: Topic name is required"
}
```

### 10.3 Невалидный режим очереди → `400 Bad Request`

```bash
curl -s -X POST http://localhost:8080/api/queues \
  -H 'Content-Type: application/json' \
  -d '{"name":"test","mode":"LIFO"}'
```

```json
{
    "timestamp": "...",
    "status": 400,
    "error": "Bad Request",
    "message": "Invalid queue mode: LIFO. Allowed: FIFO, PRIORITY"
}
```

### 10.4 Удаление несуществующей очереди → `404 Not Found`

```bash
curl -s -X DELETE http://localhost:8080/api/queues/nonexistent
```

```json
{
    "timestamp": "...",
    "status": 404,
    "error": "Not Found",
    "message": "Queue not found: nonexistent"
}
```

---

## 11. Сценарий 5: MQTT Topic — fan-out (все получают всё)

**Как работает**: каждый подписчик на топик получает **копию каждого** сообщения.

### Вариант А — ручной (3 терминала)

**Терминал 1** — подписчик 1:

```bash
mosquitto_sub -h localhost -p 1883 -t "orders" -q 1 -i sub1 -v
```

**Терминал 2** — подписчик 2:

```bash
mosquitto_sub -h localhost -p 1883 -t "orders" -q 1 -i sub2 -v
```

**Терминал 3** — публикация:

```bash
mosquitto_pub -h localhost -p 1883 -t "orders" -q 1 -m '{"item":"book","qty":1}'
mosquitto_pub -h localhost -p 1883 -t "orders" -q 1 -m '{"item":"laptop","qty":3}'
```

**Результат** — оба терминала 1 и 2 показывают:

```
orders {"item":"book","qty":1}
orders {"item":"laptop","qty":3}
```

### Вариант Б — автоматический (один терминал)

```bash
mosquitto_sub -h localhost -p 1883 -t "orders" -q 1 -i sub1 -C 2 > /tmp/sub1.txt &
mosquitto_sub -h localhost -p 1883 -t "orders" -q 1 -i sub2 -C 2 > /tmp/sub2.txt &
sleep 1
mosquitto_pub -h localhost -p 1883 -t "orders" -q 1 -m 'msg-A'
mosquitto_pub -h localhost -p 1883 -t "orders" -q 1 -m 'msg-B'
sleep 2
echo "=== Sub1 ===" && cat /tmp/sub1.txt
echo "=== Sub2 ===" && cat /tmp/sub2.txt
```

**Ожидаемый результат**:

```
=== Sub1 ===
msg-A
msg-B
=== Sub2 ===
msg-A
msg-B
```

Оба получили оба сообщения — **fan-out подтверждён**.

### Пояснение к флагам mosquitto

| Флаг     | Значение                                  |
| -------- | ----------------------------------------- |
| `-h`     | Хост брокера                              |
| `-p`     | Порт брокера                              |
| `-t`     | Имя топика                                |
| `-q 1`   | QoS 1 (at-least-once, с подтверждением)   |
| `-i`     | Client ID (должен быть **уникальным**)    |
| `-C N`   | Принять N сообщений и завершиться         |
| `-v`     | Выводить имя топика перед сообщением      |
| `-m`     | Текст сообщения для публикации            |

---

## 12. Сценарий 6: MQTT Queue — competing consumers (по одному)

**Как работает**: каждое сообщение получает **ровно один** потребитель (round-robin).

Очереди используют **MQTT Shared Subscriptions** — формат топика подписки:

```
$share/<имя_группы>/<имя_очереди>
```

### Подготовка: создать очередь

```bash
curl -s -X POST http://localhost:8080/api/queues \
  -H 'Content-Type: application/json' \
  -d '{"name":"emails","mode":"FIFO"}'
```

### Вариант А — ручной (3 терминала)

**Терминал 1** — потребитель 1:

```bash
mosquitto_sub -h localhost -p 1883 -t '$share/workers/emails' -q 1 -i worker1
```

**Терминал 2** — потребитель 2:

```bash
mosquitto_sub -h localhost -p 1883 -t '$share/workers/emails' -q 1 -i worker2
```

**Терминал 3** — публикация:

```bash
mosquitto_pub -h localhost -p 1883 -t "emails" -q 1 -m "email-1"
mosquitto_pub -h localhost -p 1883 -t "emails" -q 1 -m "email-2"
mosquitto_pub -h localhost -p 1883 -t "emails" -q 1 -m "email-3"
mosquitto_pub -h localhost -p 1883 -t "emails" -q 1 -m "email-4"
```

**Результат** — сообщения распределяются между потребителями:

```
worker1 получил: email-1, email-3
worker2 получил: email-2, email-4
```

> Точное распределение может отличаться, но каждое сообщение уходит **ровно одному** потребителю.

### Вариант Б — автоматический

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

**Ожидаемый результат**: каждый worker получил по 2 сообщения, без пересечений.

### Проверка состояния очереди после публикации

```bash
curl -s http://localhost:8080/api/queues/emails | python3 -m json.tool
```

Значение `readyCount` показывает сколько сообщений ещё ждут обработки, `inflightCount` — сколько отправлено, но ещё не подтверждено.

---

## 13. Сценарий 7: Комплексный — REST + MQTT вместе

Полный цикл: создание через REST → подписка через MQTT → публикация → проверка состояния.

### Шаг 1. Создать топик через REST

```bash
curl -s -X POST http://localhost:8080/api/topics \
  -H 'Content-Type: application/json' \
  -d '{"name":"events"}'
```

### Шаг 2. Подписаться через MQTT (в фоне)

```bash
mosquitto_sub -h localhost -p 1883 -t "events" -q 1 -i listener -C 3 > /tmp/events.txt &
sleep 1
```

### Шаг 3. Опубликовать 3 сообщения через MQTT

```bash
mosquitto_pub -h localhost -p 1883 -t "events" -q 1 -m '{"type":"user_created","id":1}'
mosquitto_pub -h localhost -p 1883 -t "events" -q 1 -m '{"type":"order_placed","id":42}'
mosquitto_pub -h localhost -p 1883 -t "events" -q 1 -m '{"type":"payment_received","id":42}'
sleep 2
```

### Шаг 4. Проверить что подписчик получил все сообщения

```bash
cat /tmp/events.txt
```

```
{"type":"user_created","id":1}
{"type":"order_placed","id":42}
{"type":"payment_received","id":42}
```

### Шаг 5. Проверить состояние топика через REST

```bash
curl -s http://localhost:8080/api/topics/events | python3 -m json.tool
```

```json
{
    "name": "events",
    "messageCount": 3,
    "latestOffset": 3,
    "createdAt": "..."
}
```

`messageCount: 3` — брокер сохранил все 3 сообщения.

### Шаг 6. Проверить health (должны быть обновлённые счётчики)

```bash
curl -s http://localhost:8080/health | python3 -m json.tool
```

`topicCount` должен включать `events`.

---

## 14. Сценарий 8: Множество подписчиков + нагрузка

### 5 подписчиков на один топик + 10 сообщений

```bash
for i in 1 2 3 4 5; do
  mosquitto_sub -h localhost -p 1883 -t "load-test" -q 1 -i "sub-$i" -C 10 > "/tmp/load-sub-$i.txt" &
done
sleep 2

for i in $(seq 1 10); do
  mosquitto_pub -h localhost -p 1883 -t "load-test" -q 1 -m "message-$i"
done
sleep 3

for i in 1 2 3 4 5; do
  echo "=== sub-$i: $(wc -l < /tmp/load-sub-$i.txt) messages ==="
done
```

**Ожидаемый результат**: каждый подписчик получил **10 сообщений**.

---

## 15. IntelliJ HTTP Client

В проекте есть файл `requests/pigeonmq.http` — его можно открыть прямо в IntelliJ IDEA.

### Как пользоваться

1. Откройте файл `requests/pigeonmq.http` в IntelliJ
2. Убедитесь, что приложение запущено на `localhost:8080`
3. Нажмите зелёную стрелку ▶ рядом с нужным запросом
4. Результат появится в панели **Run** внизу

### Содержимое файла

Файл разделён на секции:
- **HEALTH** — health check
- **TOPICS** — создание, список, детали, удаление топиков
- **QUEUES** — создание (FIFO/PRIORITY), список, детали, удаление
- **VALIDATION / ERRORS** — тесты на 404, 400 (пустое имя, невалидный mode)

---

## 16. Остановка приложения

### Локальный запуск

Нажмите `Ctrl+C` в терминале где запущен `java -jar`.

### Docker Compose

```bash
docker compose down
```

### Принудительная очистка портов

Если порты остались заняты после аварийного завершения:

```bash
# Найти и убить процессы на портах 1883 и 8080
lsof -ti :1883 -ti :8080 | sort -u | xargs kill -9
```

---

## 17. Возможные проблемы

### `Address already in use` при запуске

Порт 1883 или 8080 уже занят другим процессом.

```bash
# Посмотреть кто занимает порт
lsof -i :1883
lsof -i :8080

# Убить процесс
kill -9 <PID>
```

### `Connection refused` при подключении mosquitto

Приложение ещё не запустилось. Подождите пока в логах появится:
```
MQTT transport listening on port 1883
```

### `mosquitto_pub: command not found`

Установите mosquitto-clients:

```bash
# macOS
brew install mosquitto

# Ubuntu/Debian
sudo apt install mosquitto-clients
```

### Swagger UI не открывается

Убедитесь, что открываете именно `/swagger-ui` (а не `/swagger-ui.html`):

```
http://localhost:8080/swagger-ui
```

---

## 18. Итоговый чек-лист

Пройдите все пункты и отметьте ✅:

| #  | Тест                                                  | Способ     | Ожидание                                     |
| -- | ----------------------------------------------------- | ---------- | -------------------------------------------- |
| 1  | Приложение запускается без ошибок                     | terminal   | Лог: `Started PigeonMQApplication`           |
| 2  | MQTT-сервер слушает порт 1883                         | terminal   | Лог: `MQTT transport listening on port 1883` |
| 3  | Health check → `200`                                  | curl       | `{"status":"UP",...}`                        |
| 4  | Swagger UI открывается                                | браузер    | Страница на `/swagger-ui`                    |
| 5  | OpenAPI spec → `200`                                  | curl       | JSON на `/api-docs`                          |
| 6  | Создание топика → `201`                               | curl       | `{"success":true,...}`                       |
| 7  | Список топиков → `200`                                | curl       | Массив с созданным топиком                   |
| 8  | Детали топика → `200`                                 | curl       | Объект с `name`, `messageCount`, ...         |
| 9  | Удаление топика → `200`                               | curl       | `{"success":true,...}`                       |
| 10 | Создание FIFO очереди → `201`                         | curl       | `{"success":true,...}`                       |
| 11 | Создание PRIORITY очереди → `201`                     | curl       | `{"success":true,...}`                       |
| 12 | Список очередей → `200`                               | curl       | Массив с `mode`, `readyCount`, ...           |
| 13 | Удаление очереди → `200`                              | curl       | `{"success":true,...}`                       |
| 14 | Несуществующий ресурс → `404`                         | curl       | `{"status":404,...}`                         |
| 15 | Пустое имя → `400`                                    | curl       | `"name: Topic name is required"`             |
| 16 | Невалидный mode → `400`                               | curl       | `"Invalid queue mode: ..."`                  |
| 17 | MQTT topic: fan-out (2 подписчика)                    | mosquitto  | Оба получают **все** сообщения               |
| 18 | MQTT queue: competing consumers                       | mosquitto  | Каждое сообщение — **одному** потребителю    |
| 19 | REST + MQTT: создать → подписаться → опубликовать     | curl+mqtt  | `messageCount` растёт                        |
| 20 | Health: `activeConnections` обновляется               | curl       | > 0 при подключённых клиентах                |
| 21 | Health: `topicCount`/`queueCount` корректны           | curl       | Совпадают с фактическим числом               |
| 22 | Нагрузка: 5 подписчиков × 10 сообщений               | mosquitto  | Каждый получил по 10                         |
