package fr.traodev.rest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.traodev.util.Json;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

public final class RestClient {
    public static final String DEFAULT_API_BASE = "https://discord.com/api/v10";
    public static final String VERSION = "0.1.0";
    private static final String USER_AGENT = "DiscordBot (https://github.com/traodev/jdisc, " + VERSION + ")";

    private final String base = System.getProperty("jdisc.api.base", DEFAULT_API_BASE);
    private final HttpClient http;
    private final String authorization;
    private final RateLimiter limiter;

    public RestClient(String token, ScheduledExecutorService scheduler, Executor executor) {
        this.authorization = "Bot " + token;
        this.http = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_2)
            .connectTimeout(Duration.ofSeconds(10))
            .executor(executor)
            .build();
        this.limiter = new RateLimiter(scheduler, executor, this::send);
    }

    HttpClient httpClient() {
        return http;
    }

    public CompletableFuture<JsonNode> request(Route route) {
        return request(route, null, null, null);
    }

    public CompletableFuture<JsonNode> request(Route route, Object body) {
        return request(route, body, null, null);
    }

    public <T> CompletableFuture<T> request(Route route, Object body, Class<T> type) {
        return request(route, body).thenApply(n -> Json.convert(n, type));
    }

    public <T> CompletableFuture<List<T>> requestList(Route route, Object body, Class<T> elementType) {
        return request(route, body).thenApply(n -> {
            var out = new java.util.ArrayList<T>(n.size());
            for (JsonNode e : n) out.add(Json.convert(e, elementType));
            return out;
        });
    }

    public CompletableFuture<JsonNode> request(Route route, Object body, List<FileUpload> files, String auditReason) {
        byte[] bytes = null;
        String contentType = null;
        if (files != null && !files.isEmpty()) {
            ObjectNode payload = body == null ? Json.object() : (body instanceof ObjectNode o ? o : Json.MAPPER.valueToTree(body));
            Multipart m = Multipart.build(payload, files);
            bytes = m.body;
            contentType = m.contentType;
        } else if (body != null) {
            bytes = Json.toBytes(body);
            contentType = "application/json";
        }
        PendingRequest req = new PendingRequest(route, bytes, contentType, auditReason);
        limiter.submit(req);
        return req.result;
    }

    private CompletableFuture<HttpResponse<byte[]>> send(PendingRequest r) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + r.route.path()))
            .timeout(Duration.ofSeconds(30))
            .header("Authorization", authorization)
            .header("User-Agent", USER_AGENT);
        if (r.auditReason != null)
            b.header("X-Audit-Log-Reason", URLEncoder.encode(r.auditReason, StandardCharsets.UTF_8).replace("+", "%20"));
        if (r.body != null) {
            b.header("Content-Type", r.contentType);
            b.method(r.route.method(), HttpRequest.BodyPublishers.ofByteArray(r.body));
        } else {
            b.method(r.route.method(), HttpRequest.BodyPublishers.noBody());
        }
        return http.sendAsync(b.build(), HttpResponse.BodyHandlers.ofByteArray());
    }
}
