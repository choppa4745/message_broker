package com.pigeonmq.sdk.admin;

import com.pigeonmq.sdk.exception.PigeonException;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;

public final class PigeonAdminClientBuilder {

    private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private String baseUrl = "http://localhost:8080";
    private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
    private Duration requestTimeout = DEFAULT_REQUEST_TIMEOUT;
    private HttpClient httpClient;

    PigeonAdminClientBuilder() {}

    public PigeonAdminClientBuilder baseUrl(String baseUrl) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        return this;
    }

    public PigeonAdminClientBuilder connectTimeout(Duration connectTimeout) {
        this.connectTimeout = Objects.requireNonNull(connectTimeout, "connectTimeout");
        return this;
    }

    public PigeonAdminClientBuilder requestTimeout(Duration requestTimeout) {
        this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout");
        return this;
    }

    /**
     * Provide your own {@link HttpClient}. When set, {@link #connectTimeout(Duration)} is ignored.
     */
    public PigeonAdminClientBuilder httpClient(HttpClient httpClient) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient");
        return this;
    }

    public PigeonAdminClient build() {
        URI uri = parseUri(baseUrl);
        HttpClient client = httpClient != null
                ? httpClient
                : HttpClient.newBuilder().connectTimeout(connectTimeout).build();
        return new PigeonAdminClient(uri, client, requestTimeout);
    }

    private static URI parseUri(String baseUrl) {
        try {
            URI uri = URI.create(baseUrl);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new PigeonException("baseUrl must include scheme and host: " + baseUrl);
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw new PigeonException("Invalid baseUrl: " + baseUrl, e);
        }
    }
}
