# pigeonMQ — Брокер сообщений

Собственный брокер сообщений с поддержкой паттерна `Publisher/Subscriber` и очередей, персистентностью и гарантией доставки `at-least-once`.

## Ключевые возможности


| Возможность         | Описание                                                                                |
| ------------------- | --------------------------------------------------------------------------------------- |
| **Pub/Sub**         | Fan-out доставка: каждый подписчик топика получает копию каждого сообщения              |
| **Очереди**         | Point-to-point доставка: каждое сообщение получает ровно один потребитель (round-robin) |
| **FIFO / Priority** | Очереди поддерживают FIFO-порядок и приоритетную выборку                                |
| **Персистентность** | Сообщения сохраняются в PostgreSQL и восстанавливаются после перезапуска                |
| **At-least-once**   | ACK/NACK, повторная доставка по таймауту, Dead Letter Queue                             |
| **TTL**             | Автоматическое удаление просроченных сообщений                                          |
| **Метрики**         | Prometheus endpoint `/metrics`                                                          |
| **REST API**        | HTTP API для управления и мониторинга                                                   |
| **MQTT-протокол**   | Доставка сообщений поверх MQTT (QoS + shared subscriptions)                             |
| **Клиентский SDK**  | Java-клиент (`com.pigeonmq.client`)                                                     |


## Архитектура (верхний уровень)

```
┌──────────────┐      ┌──────────────┐      ┌──────────────┐
│  Publisher    │      │  Publisher    │      │  Subscriber   │
│  (Service A)  │      │  (Service B)  │      │  (Service C)  │
└──────┬───────┘      └──────┬───────┘      └──────▲───────┘
       │ MQTT                 │ MQTT                 │ MQTT
       ▼                      ▼                      │
┌──────────────────────────────────────────────────────────┐
│                        pigeonMQ Broker                      │
│                                                           │
│  ┌─────────────┐  ┌─────────────┐  ┌──────────────────┐ │
│  │  Transport   │  │  Broker     │  │  Storage Engine  │ │
│  │  Layer       │──▶│  Core       │──▶│  (PostgreSQL)     │ │
│  │  (MQTT+HTTP) │  │             │  │                  │ │
│  └─────────────┘  └─────────────┘  └──────────────────┘ │
└──────────────────────────────────────────────────────────┘
```

Подробная архитектура: `[docs/architecture.md](docs/architecture.md)`

## Технический стек


| Компонент  | Технология                              | Обоснование                                               |
| ---------- | --------------------------------------- | --------------------------------------------------------- |
| Брокер     | **Java 17 (LTS)**                       | Надёжная платформа, совместимость с текущей экосистемой   |
| Сборка     | **Maven**                               | Стандартный build, проще для Docker и CI                  |
| Хранение   | **PostgreSQL**                          | Транзакционная персистентность и recovery                 |
| Протокол   | **MQTT 5.0**                            | Готовая model pub/sub + shared subscriptions для очередей |
| Управление | **REST API** (`com.sun.net.httpserver`) | Встроенный HTTP-сервер, нет лишних зависимостей           |
| Метрики    | **Prometheus**                          | Стандарт индустрии                                        |
| SDK        | **Java** (`com.pigeonmq.client`)        | Единый язык, общий код протокола, нет лишних зависимостей |
| Деплой     | **Docker + docker-compose**             | Воспроизводимость, три тестовых сервиса в одном compose   |


Подробное обоснование: `[docs/tech-stack.md](docs/tech-stack.md)`

## Структура проекта

```
message_broker/
├── src/
│   └── main/
│       ├── java/com/pigeonmq/
│       │   ├── PigeonMQ.java                  # Точка входа брокера
│       │   ├── config/
│       │   │   └── BrokerConfig.java           # Конфигурация из YAML/ENV
│       │   ├── broker/
│       │   │   ├── Broker.java                 # Главный оркестратор
│       │   │   ├── Message.java                # Модель сообщения
│       │   │   ├── Topic.java                  # Управление топиками (fan-out)
│       │   │   ├── MessageQueue.java           # Управление очередями (point-to-point)
│       │   │   ├── Subscription.java           # Реестр подписок
│       │   │   ├── MessageRouter.java          # Маршрутизация сообщений
│       │   │   └── DeliveryManager.java        # ACK/NACK, retry, DLQ
│       │   ├── storage/
│       │   │   ├── PostgresMessageStore.java   # persist сообщений и offset’ов
│       │   │   ├── QueueDeliveryStateDao.java  # READY/INFLIGHT/DLQ состояния очередей
│       │   │   └── DbRecovery.java             # восстановление после restart
│       │   ├── transport/
│       │   │   ├── mqtt/
│       │   │   │   ├── MqttServer.java         # MQTT-сервер (broker transport)
│       │   │   │   ├── MqttClientSession.java # Управление сессией/подписками
│       │   │   │   └── MqttCodec.java          # Кодирование/декодирование MQTT packets
│       │   │   └── http/
│       │   │       ├── HttpApiServer.java       # HTTP-сервер (REST API)
│       │   │       └── ApiHandlers.java         # Обработчики эндпоинтов
│       │   └── metrics/
│       │       └── PrometheusMetrics.java       # Метрики Prometheus
│       └── resources/
│           └── broker.yaml                      # Конфигурация по умолчанию
├── client/
│   └── src/main/java/com/pigeonmq/client/
│       ├── PigeonMQClient.java               # Java SDK — общий клиент
│       ├── Publisher.java                       # Java SDK — Publisher
│       └── Subscriber.java                      # Java SDK — Subscriber
├── examples/
│   ├── PublisherExample.java                    # Пример publisher-сервиса
│   └── SubscriberExample.java                   # Пример subscriber-сервиса
├── deployments/
│   ├── Dockerfile                               # Мульти-стейдж сборка
│   └── docker-compose.yml                       # Брокер + 1 publisher + 2 subscriber
├── docs/
│   ├── architecture.md                          # Архитектурная схема
│   ├── protocol.md                              # Описание бинарного протокола
│   ├── data-structures.md                       # Структуры данных
│   ├── tech-stack.md                            # Обоснование технологий
│   └── implementation-plan.md                   # План реализации
├── pom.xml                                       # Maven multi-module конфигурация
└── README.md                                    # Этот файл
```

## Быстрый старт

### Сборка из исходников

```bash
mvn package -DskipTests
java -jar target/pigeonmq-0.1.0-SNAPSHOT.jar
```

### Docker

```bash
cp .env.example .env
docker compose up --build
```

Это поднимет:

- **pigeonMQ Broker** на портах `1883` (MQTT) и `8080` (HTTP)
- **PostgreSQL** на порту `5432`

### Пример использования (Java SDK)

```java
import com.pigeonmq.client.Publisher;
import com.pigeonmq.client.Subscriber;

// Публикация
try (var pub = new Publisher("localhost", 1883)) {
    pub.connect();
    pub.publish("orders", """
        {"item": "book", "qty": 2}
        """.getBytes());
}

// Подписка
try (var sub = new Subscriber("localhost", 1883)) {
    sub.connect();
    sub.subscribe("orders", message -> {
        System.out.println("Received: " + new String(message.payload()));
        message.ack();
    });
    sub.listen();
}
```

## REST API (управление)


| Метод    | Эндпоинт             | Описание              |
| -------- | -------------------- | --------------------- |
| `GET`    | `/api/topics`        | Список всех топиков   |
| `POST`   | `/api/topics`        | Создать топик         |
| `DELETE` | `/api/topics/{name}` | Удалить топик         |
| `GET`    | `/api/topics/{name}` | Информация о топике   |
| `GET`    | `/api/queues`        | Список всех очередей  |
| `POST`   | `/api/queues`        | Создать очередь       |
| `DELETE` | `/api/queues/{name}` | Удалить очередь       |
| `GET`    | `/api/queues/{name}` | Информация об очереди |
| `GET`    | `/health`            | Health check          |
| `GET`    | `/metrics`           | Prometheus метрики    |


## Документация

- [Архитектура системы](docs/architecture.md)
- [Протокол pigeonMQ (MQTT)](docs/protocol.md)
- [Структуры данных](docs/data-structures.md)
- [Технический стек](docs/tech-stack.md)
- [План реализации](docs/implementation-plan.md)

## Лицензия

MIT