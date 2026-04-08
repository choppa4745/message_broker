package com.pigeonmq.transport.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.pigeonmq.config.BrokerConfig;
import com.pigeonmq.core.Broker;
import com.pigeonmq.core.QueueManager;
import com.pigeonmq.core.TopicManager;
import com.pigeonmq.model.QueueMode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

public class HttpApiServer {

    private static final Logger log = LoggerFactory.getLogger(HttpApiServer.class);

    private final BrokerConfig config;
    private final Broker broker;
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;

    public HttpApiServer(BrokerConfig config, Broker broker) {
        this.config = config;
        this.broker = broker;
    }

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(config.getHttpPort()), 0);
        server.createContext("/api/topics", this::handleTopics);
        server.createContext("/api/queues", this::handleQueues);
        server.createContext("/health", this::handleHealth);
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        log.info("HTTP API listening on port {}", config.getHttpPort());
    }

    public void stop() {
        if (server != null) server.stop(1);
        log.info("HTTP API stopped");
    }

    /* ═══════════════ /api/topics ═══════════════ */

    private void handleTopics(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();

            if ("GET".equals(method) && "/api/topics".equals(path)) {
                listTopics(ex);
            } else if ("POST".equals(method) && "/api/topics".equals(path)) {
                createTopic(ex);
            } else if ("DELETE".equals(method) && path.startsWith("/api/topics/")) {
                String name = path.substring("/api/topics/".length());
                deleteTopic(ex, name);
            } else if ("GET".equals(method) && path.startsWith("/api/topics/")) {
                String name = path.substring("/api/topics/".length());
                getTopic(ex, name);
            } else {
                respond(ex, 405, errorJson("Method not allowed"));
            }
        } catch (Exception e) {
            log.error("HTTP error", e);
            respond(ex, 500, errorJson(e.getMessage()));
        }
    }

    private void listTopics(HttpExchange ex) throws IOException {
        ArrayNode arr = json.createArrayNode();
        for (TopicManager tm : broker.getTopics().values()) {
            ObjectNode node = json.createObjectNode();
            node.put("name", tm.getName());
            node.put("messageCount", tm.getMessageCount());
            node.put("latestOffset", tm.getLatestOffset());
            node.put("createdAt", tm.getCreatedAt().toString());
            arr.add(node);
        }
        ObjectNode root = json.createObjectNode();
        root.set("topics", arr);
        respond(ex, 200, json.writeValueAsString(root));
    }

    private void getTopic(HttpExchange ex, String name) throws IOException {
        TopicManager tm = broker.getTopic(name);
        if (tm == null) {
            respond(ex, 404, errorJson("Topic not found: " + name));
            return;
        }
        ObjectNode node = json.createObjectNode();
        node.put("name", tm.getName());
        node.put("messageCount", tm.getMessageCount());
        node.put("latestOffset", tm.getLatestOffset());
        node.put("createdAt", tm.getCreatedAt().toString());
        respond(ex, 200, json.writeValueAsString(node));
    }

    private void createTopic(HttpExchange ex) throws IOException {
        Map<?, ?> body = json.readValue(ex.getRequestBody(), Map.class);
        String name = (String) body.get("name");
        if (name == null || name.isBlank()) {
            respond(ex, 400, errorJson("'name' is required"));
            return;
        }
        broker.createTopic(name);
        ObjectNode res = json.createObjectNode();
        res.put("created", true);
        res.put("name", name);
        respond(ex, 201, json.writeValueAsString(res));
    }

    private void deleteTopic(HttpExchange ex, String name) throws IOException {
        boolean ok = broker.deleteTopic(name);
        ObjectNode res = json.createObjectNode();
        res.put("deleted", ok);
        res.put("name", name);
        respond(ex, ok ? 200 : 404, json.writeValueAsString(res));
    }

    /* ═══════════════ /api/queues ═══════════════ */

    private void handleQueues(HttpExchange ex) throws IOException {
        try {
            String method = ex.getRequestMethod();
            String path = ex.getRequestURI().getPath();

            if ("GET".equals(method) && "/api/queues".equals(path)) {
                listQueues(ex);
            } else if ("POST".equals(method) && "/api/queues".equals(path)) {
                createQueue(ex);
            } else if ("DELETE".equals(method) && path.startsWith("/api/queues/")) {
                String name = path.substring("/api/queues/".length());
                deleteQueue(ex, name);
            } else if ("GET".equals(method) && path.startsWith("/api/queues/")) {
                String name = path.substring("/api/queues/".length());
                getQueue(ex, name);
            } else {
                respond(ex, 405, errorJson("Method not allowed"));
            }
        } catch (Exception e) {
            log.error("HTTP error", e);
            respond(ex, 500, errorJson(e.getMessage()));
        }
    }

    private void listQueues(HttpExchange ex) throws IOException {
        ArrayNode arr = json.createArrayNode();
        for (QueueManager qm : broker.getQueues().values()) {
            arr.add(queueToJson(qm));
        }
        ObjectNode root = json.createObjectNode();
        root.set("queues", arr);
        respond(ex, 200, json.writeValueAsString(root));
    }

    private void getQueue(HttpExchange ex, String name) throws IOException {
        QueueManager qm = broker.getQueue(name);
        if (qm == null) {
            respond(ex, 404, errorJson("Queue not found: " + name));
            return;
        }
        respond(ex, 200, json.writeValueAsString(queueToJson(qm)));
    }

    private void createQueue(HttpExchange ex) throws IOException {
        Map<?, ?> body = json.readValue(ex.getRequestBody(), Map.class);
        String name = (String) body.get("name");
        if (name == null || name.isBlank()) {
            respond(ex, 400, errorJson("'name' is required"));
            return;
        }
        Object modeRaw = body.get("mode");
        String modeStr = modeRaw != null ? modeRaw.toString() : "FIFO";
        QueueMode mode;
        try {
            mode = QueueMode.valueOf(modeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            respond(ex, 400, errorJson("Invalid mode: " + modeStr + " (use FIFO or PRIORITY)"));
            return;
        }
        broker.createQueue(name, mode);
        ObjectNode res = json.createObjectNode();
        res.put("created", true);
        res.put("name", name);
        res.put("mode", mode.name());
        respond(ex, 201, json.writeValueAsString(res));
    }

    private void deleteQueue(HttpExchange ex, String name) throws IOException {
        boolean ok = broker.deleteQueue(name);
        ObjectNode res = json.createObjectNode();
        res.put("deleted", ok);
        res.put("name", name);
        respond(ex, ok ? 200 : 404, json.writeValueAsString(res));
    }

    private ObjectNode queueToJson(QueueManager qm) {
        ObjectNode node = json.createObjectNode();
        node.put("name", qm.getName());
        node.put("mode", qm.getMode().name());
        node.put("readyCount", qm.getReadyCount());
        node.put("inflightCount", qm.getInflightCount());
        node.put("createdAt", qm.getCreatedAt().toString());
        return node;
    }

    /* ═══════════════ /health ═══════════════ */

    private void handleHealth(HttpExchange ex) throws IOException {
        ObjectNode node = json.createObjectNode();
        node.put("status", "UP");
        node.put("topics", broker.getTopics().size());
        node.put("queues", broker.getQueues().size());
        respond(ex, 200, json.writeValueAsString(node));
    }

    /* ═══════════════ Helpers ═══════════════ */

    private void respond(HttpExchange ex, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json");
        ex.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private String errorJson(String message) {
        return "{\"error\":\"" + message.replace("\"", "'") + "\"}";
    }
}
