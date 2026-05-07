package com.practice3.loadgen;

import com.google.gson.Gson;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ApiClient {

    private final String baseUrl;
    private final HttpClient client;
    private final Gson gson = new Gson();

    public ApiClient(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    public void reset() throws Exception {
        withRetry("reset", 5, () -> {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/admin/reset"))
                    .timeout(Duration.ofSeconds(120))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<Void> resp = client.send(req, HttpResponse.BodyHandlers.discarding());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("reset http " + resp.statusCode());
            }
            return null;
        });
    }

    public StatsSnapshot stats() throws Exception {
        return withRetry("stats", 5, () -> {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/stats"))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                throw new RuntimeException("stats http " + resp.statusCode());
            }
            return gson.fromJson(resp.body(), StatsSnapshot.class);
        });
    }

    public HttpClient http() {
        return client;
    }

    public String baseUrl() {
        return baseUrl;
    }

    private static <T> T withRetry(String name, int attempts, ThrowingSupplier<T> fn) throws Exception {
        long backoffMs = 200;
        Exception last = null;
        for (int i = 1; i <= attempts; i++) {
            try {
                return fn.get();
            } catch (Exception e) {
                last = e;
                if (i == attempts) break;
                Thread.sleep(backoffMs);
                backoffMs = Math.min(2_000, backoffMs * 2);
            }
        }
        throw new RuntimeException(name + " failed after " + attempts + " attempts", last);
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}

