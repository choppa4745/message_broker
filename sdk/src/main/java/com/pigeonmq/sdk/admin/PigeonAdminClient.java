package com.pigeonmq.sdk.admin;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pigeonmq.sdk.admin.dto.HealthInfo;
import com.pigeonmq.sdk.admin.dto.MutationResponse;
import com.pigeonmq.sdk.admin.dto.QueueInfo;
import com.pigeonmq.sdk.admin.dto.TopicInfo;
import com.pigeonmq.sdk.exception.AdminException;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * REST client for the pigeonMQ management API.
 *
 * Supports topic/queue CRUD and health checks. Uses {@link HttpClient} for transport
 * and Jackson for JSON (de)serialization.
 */
public final class PigeonAdminClient {

    private static final String CONTENT_TYPE = "application/json";

    public static PigeonAdminClientBuilder builder() {
        return new PigeonAdminClientBuilder();
    }

    private final URI baseUri;
    private final HttpClient http;
    private final Duration requestTimeout;
    private final ObjectMapper mapper;

    PigeonAdminClient(URI baseUri, HttpClient http, Duration requestTimeout) {
        this.baseUri = baseUri;
        this.http = http;
        this.requestTimeout = requestTimeout;
        this.mapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    // ── Health ─────────────────────────────────────────────────────────

    public HealthInfo health() {
        return get("/health", HealthInfo.class);
    }

    // ── Topics ─────────────────────────────────────────────────────────

    public List<TopicInfo> listTopics() {
        return getList("/api/topics", new TypeReference<>() {});
    }

    public TopicInfo getTopic(String name) {
        return get("/api/topics/" + requireName(name), TopicInfo.class);
    }

    public MutationResponse createTopic(String name) {
        return post("/api/topics", Map.of("name", requireName(name)), MutationResponse.class);
    }

    public MutationResponse deleteTopic(String name) {
        return delete("/api/topics/" + requireName(name), MutationResponse.class);
    }

    // ── Queues ─────────────────────────────────────────────────────────

    public List<QueueInfo> listQueues() {
        return getList("/api/queues", new TypeReference<>() {});
    }

    public QueueInfo getQueue(String name) {
        return get("/api/queues/" + requireName(name), QueueInfo.class);
    }

    public MutationResponse createQueue(String name, QueueMode mode) {
        Map<String, String> body = Map.of(
                "name", requireName(name),
                "mode", Objects.requireNonNull(mode, "mode").name());
        return post("/api/queues", body, MutationResponse.class);
    }

    public MutationResponse deleteQueue(String name) {
        return delete("/api/queues/" + requireName(name), MutationResponse.class);
    }

    // ── HTTP helpers ───────────────────────────────────────────────────

    private <T> T get(String path, Class<T> type) {
        HttpRequest request = baseRequest(path).GET().build();
        return send(request, type);
    }

    private <T> List<T> getList(String path, TypeReference<List<T>> typeRef) {
        HttpRequest request = baseRequest(path).GET().build();
        return sendList(request, typeRef);
    }

    private <T> T post(String path, Object body, Class<T> type) {
        HttpRequest request = baseRequest(path)
                .header("Content-Type", CONTENT_TYPE)
                .POST(HttpRequest.BodyPublishers.ofString(toJson(body)))
                .build();
        return send(request, type);
    }

    private <T> T delete(String path, Class<T> type) {
        HttpRequest request = baseRequest(path).DELETE().build();
        return send(request, type);
    }

    private HttpRequest.Builder baseRequest(String path) {
        return HttpRequest.newBuilder()
                .uri(baseUri.resolve(path))
                .timeout(requestTimeout)
                .header("Accept", CONTENT_TYPE);
    }

    private <T> T send(HttpRequest request, Class<T> type) {
        HttpResponse<String> response = sendRaw(request);
        ensureSuccess(request, response);
        return parse(response.body(), type);
    }

    private <T> List<T> sendList(HttpRequest request, TypeReference<List<T>> typeRef) {
        HttpResponse<String> response = sendRaw(request);
        ensureSuccess(request, response);
        return parseList(response.body(), typeRef);
    }

    private HttpResponse<String> sendRaw(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (java.io.IOException e) {
            throw new AdminException(
                    "HTTP " + request.method() + " " + request.uri() + " failed: " + e.getMessage(),
                    -1, e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AdminException("HTTP request interrupted", -1, e);
        }
    }

    private void ensureSuccess(HttpRequest request, HttpResponse<String> response) {
        int status = response.statusCode();
        if (status / 100 != 2) {
            throw new AdminException(
                    "HTTP " + request.method() + " " + request.uri()
                            + " returned " + status + ": " + response.body(),
                    status);
        }
    }

    private <T> T parse(String json, Class<T> type) {
        try {
            return mapper.readValue(json, type);
        } catch (java.io.IOException e) {
            throw new AdminException("Failed to parse JSON response: " + e.getMessage(), -1, e);
        }
    }

    private <T> List<T> parseList(String json, TypeReference<List<T>> typeRef) {
        try {
            return mapper.readValue(json, typeRef);
        } catch (java.io.IOException e) {
            throw new AdminException("Failed to parse JSON response: " + e.getMessage(), -1, e);
        }
    }

    private String toJson(Object body) {
        try {
            return mapper.writeValueAsString(body);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new AdminException("Failed to encode JSON request: " + e.getMessage(), -1, e);
        }
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return name;
    }
}
