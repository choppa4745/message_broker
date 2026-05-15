# pigeonMQ Java SDK

Клиентская библиотека на Java для брокера **pigeonMQ**.

В SDK входят:

- **`PigeonClient`** — асинхронный MQTT v5-клиент с переподключением, структурированными исключениями и обработчиками по подпискам.
- **`PigeonAdminClient`** — REST-клиент для управления топиками и очередями, а также проверки health.

Транспорт построен на [Eclipse Paho MQTT v5](https://github.com/eclipse/paho.mqtt.java): конечная точка брокера (порт `1883`) и любой сторонний MQTT 5 брокер поддерживаются так же.

## Координаты Maven

```xml
<dependency>
    <groupId>com.pigeonmq</groupId>
    <artifactId>pigeonmq-sdk</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Быстрый старт

```java
import com.pigeonmq.sdk.PigeonClient;
import com.pigeonmq.sdk.QoS;

PigeonClient client = PigeonClient.builder()
        .host("localhost")
        .port(1883)
        .clientId("orders-worker-1")
        .cleanStart(true)                 // по умолчанию; false — сохранить сессию/offset на брокере
        .build();

client.connect();

// Топик (fan-out)
client.subscribeTopic("orders", QoS.AT_LEAST_ONCE, msg ->
        System.out.println("topic msg: " + msg.payloadAsString()));

// Очередь (competing consumers). SDK сам собирает "$share/<group>/<queue>"
client.subscribeQueue("emails", "workers", QoS.AT_LEAST_ONCE, msg ->
        System.out.println("queue msg: " + msg.payloadAsString()));

client.publish("orders", "hello".getBytes(), QoS.AT_LEAST_ONCE);

client.disconnect();
client.close();
```

### Асинхронная публикация

```java
client.publishAsync("orders", "hello".getBytes(), QoS.AT_LEAST_ONCE)
        .thenRun(() -> System.out.println("получен PUBACK"))
        .exceptionally(err -> { err.printStackTrace(); return null; });
```

### События соединения и свой executor

```java
import java.util.concurrent.Executors;

PigeonClient client = PigeonClient.builder()
        .host("localhost")
        .port(1883)
        .clientId("orders-worker-1")
        .callbackExecutor(Executors.newFixedThreadPool(2))
        .connectionListener(new ConnectionListener() {
            @Override public void onConnected(String uri)    { System.out.println("подключено к " + uri); }
            @Override public void onReconnected(String uri)  { System.out.println("переподключено к " + uri); }
            @Override public void onConnectionLost(Throwable t) { System.out.println("потеря соединения: " + t); }
        })
        .build();
```

Все вызовы `MessageHandler` и `ConnectionListener` выполняются на переданном **executor** — поток ввода-вывода Paho пользовательским кодом не блокируется. Если executor не задан, создаётся однопоточный daemon-executor и закрывается вместе с `close()`.

## Админ-клиент (REST)

```java
import com.pigeonmq.sdk.admin.PigeonAdminClient;
import com.pigeonmq.sdk.admin.QueueMode;

PigeonAdminClient admin = PigeonAdminClient.builder()
        .baseUrl("http://localhost:8080")
        .build();

admin.createTopic("orders");
admin.createQueue("emails", QueueMode.FIFO);

admin.listTopics().forEach(t -> System.out.println(t.name()));
admin.listQueues().forEach(q -> System.out.println(q.name() + " mode=" + q.mode()));

System.out.println(admin.health().status());
```

При ответах не 2xx методы бросают `AdminException` с HTTP-кодом в `httpStatus()`.

## Обработка ошибок

| Исключение | Когда возникает |
|------------|-----------------|
| `ConnectionException` | сбой connect/disconnect, вызов при неподключённом клиенте |
| `PublishException` | отклонённая публикация или ошибка транспорта |
| `SubscriptionException` | ошибка subscribe/unsubscribe |
| `AdminException` | неуспешный REST (4xx/5xx, сеть, JSON) — код в `httpStatus()` |
| `PigeonException` | базовый тип для перечисленного выше |

## Подсказки

- Для **топиков** держите стабильный `clientId` и при необходимости выставьте `cleanStart(false)`, чтобы брокер помнил offset после рестартов (таблица `consumer_offsets`).
- Для **очередей** аргумент `group` — группа shared subscription в MQTT: потребители в одной группе делят сообщения, в разных группах получают независимые копии.
- `publishAsync(...)` завершается, когда брокер подтвердил приём (для QoS 1 — `PUBACK`). Она **не ждёт**, пока подписчик прочитает сообщение.
