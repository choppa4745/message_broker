# Практика 2: Сравнение RabbitMQ и Redis как брокеров сообщений

## Цель

Провести серию нагрузочных тестов и сравнить RabbitMQ и Redis в одинаковых условиях по следующим параметрам:

- Пропускная способность (messages/sec)
- Задержка (avg, P50, P95, P99, max)
- Влияние размера сообщения (128 B → 100 KB)
- Точка деградации single instance

## Архитектура стенда

```
┌──────────┐      ┌──────────────────┐      ┌──────────┐
│ Producer │ ───▶ │  RabbitMQ / Redis │ ───▶ │ Consumer │
│ (Thread) │      │  (Docker, 1 CPU, │      │ (Thread) │
│          │      │   512 MB RAM)    │      │          │
└──────────┘      └──────────────────┘      └──────────┘
```

- **Producer** — генерирует сообщения с меткой времени и отправляет в брокер с заданной скоростью.
- **Broker** — RabbitMQ (queue: `basicPublish` / `basicConsume`) или Redis (list: `LPUSH` / `BRPOP`).
- **Consumer** — читает сообщения, вычисляет задержку, считает полученные.

Оба брокера запущены в Docker с **одинаковыми ресурсными лимитами** (1 CPU, 512 MB RAM).

## Эксперименты


| #   | Эксперимент       | Что варьируем               | Фиксировано       |
| --- | ----------------- | --------------------------- | ----------------- |
| 1   | Базовое сравнение | —                           | 1 KB, 1 000 msg/s |
| 2   | Влияние размера   | 128 B, 1 KB, 10 KB, 100 KB  | 1 000 msg/s       |
| 3   | Стресс-тест       | 1K, 5K, 10K, 20K, 50K msg/s | 1 KB              |


## Что измеряется

- Messages sent / received / lost
- Send / receive rate (msg/s)
- Avg / P50 / P95 / P99 / Max latency (ms)
- Количество ошибок
- Точка деградации (потери > 5%, P95 > 100 ms, ошибки)

## Требования

- Java 17+
- Maven 3.8+
- Docker + Docker Compose v2

## Быстрый запуск

```bash
# Полный прогон (~15 мин)
./run.sh

# Быстрый прогон (~5 мин) — меньше размеров и скоростей, 10s на тест
./run.sh --quick

# Указать длительность каждого теста
./run.sh --duration 20
```

## Запуск вручную

```bash
# 1. Запустить брокеры
docker compose up -d

# 2. Дождаться готовности
docker exec bench-rabbitmq rabbitmq-diagnostics -q ping
docker exec bench-redis redis-cli ping

# 3. Собрать проект
mvn clean package -q

# 4. Запустить бенчмарк
java -jar target/broker-benchmark-1.0.0.jar
java -jar target/broker-benchmark-1.0.0.jar --quick
java -jar target/broker-benchmark-1.0.0.jar --duration 15

# 5. Остановить брокеры
docker compose down
```

## Результаты

После запуска в папке `results/` появятся:


| Файл               | Описание                                              |
| ------------------ | ----------------------------------------------------- |
| `report.md`        | Markdown-отчёт с таблицами и автоматическими выводами |
| `raw_results.json` | Сырые данные всех экспериментов в JSON                |


## Структура проекта

```
practice2/
├── docker-compose.yml                   # RabbitMQ + Redis
├── pom.xml                              # Maven
├── run.sh                               # Скрипт запуска
├── README.md
├── results/                             # Результаты (генерируется)
└── src/main/java/com/benchmark/
    ├── BenchmarkApp.java                # Точка входа + оркестрация
    ├── ExperimentConfig.java            # Конфигурация эксперимента
    ├── ExperimentResult.java            # Модель результатов
    ├── ProducerWorker.java              # Producer-поток
    ├── ConsumerWorker.java              # Consumer-поток
    ├── ExperimentRunner.java            # Запуск одного эксперимента
    └── ReportGenerator.java             # Генерация отчёта
```

## Технологии

- **RabbitMQ 3** (AMQP 0-9-1) — `amqp-client`
- **Redis 7** (Lists: LPUSH/BRPOP) — `jedis`
- **Gson** — сериализация результатов в JSON
- **Maven Shade Plugin** — uber-JAR для удобного запуска

