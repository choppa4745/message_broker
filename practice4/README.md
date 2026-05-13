# Практика 4: аномалии изоляции в SQL

Проект воспроизводит типичные аномалии параллельных транзакций на **PostgreSQL 16** (Docker).

## Быстрый старт

```bash
cd practice4
docker compose up -d
```

Строка подключения:

```text
postgresql://lab_user:lab_pass@localhost:5441/isolation_lab
```

Инициализация таблиц и данных:

```bash
psql "postgresql://lab_user:lab_pass@localhost:5441/isolation_lab" \
  -f sql/00_reset.sql

psql "postgresql://lab_user:lab_pass@localhost:5441/isolation_lab" \
  -f sql/01_schema_and_seed.sql
```

## Скрипты

| Файл | Назначение |
|------|------------|
| `sql/00_reset.sql` | Удаление таблиц |
| `sql/01_schema_and_seed.sql` | Создание `accounts`, `orders`, тестовые строки |
| `sql/02_non_repeatable_read.sql` | Инструкция: неповторяемое чтение (два терминала) |
| `sql/03_phantom_read.sql` | Инструкция: фантомное чтение |
| `sql/04_lost_update.sql` | Инструкция: потерянное обновление + сброс баланса |
| `sql/05_dirty_read_postgresql_note.sql` | Почему классический dirty read в PG недоступен |

Демонстрации **non-repeatable read**, **phantom read** и **lost update** требуют **двух одновременных сессий** `psql` — скопируйте блоки «Сессия A / Сессия B» из соответствующих `.sql` файлов по шагам.

После работы:

```bash
docker compose down
```

Подробная инструкция по проверке всех сценариев: **`TESTING.md`**.

Подробный отчёт для сдачи: **`report.md`** (разделы со скриншотами заполнить вручную).
