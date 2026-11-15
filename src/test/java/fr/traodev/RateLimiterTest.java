package fr.traodev.rest;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;

import javax.net.ssl.SSLSession;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterTest {
    static final class Fake implements HttpResponse<byte[]> {
        private final int status;
        private final String bodyText;
        private final Map<String, List<String>> headerMap;

        Fake(int status, String body, Map<String, List<String>> headers) { this.status = status; this.bodyText = body; this.headerMap = headers; }

        public int statusCode() { return status; }
        public HttpRequest request() { return null; }
        public Optional<HttpResponse<byte[]>> previousResponse() { return Optional.empty(); }
        public HttpHeaders headers() { return HttpHeaders.of(headerMap, (a, b) -> true); }
        public byte[] body() { return bodyText.getBytes(StandardCharsets.UTF_8); }
        public Optional<SSLSession> sslSession() { return Optional.empty(); }
        public URI uri() { return URI.create("http://x"); }
        public HttpClient.Version version() { return HttpClient.Version.HTTP_1_1; }
    }

    private RateLimiter limiter(Function<PendingRequest, HttpResponse<byte[]>> fn, ScheduledExecutorService s) {
        return new RateLimiter(s, Runnable::run, r -> CompletableFuture.completedFuture(fn.apply(r)));
    }

    @Test
    void retriesAfter429AndSucceeds() throws Exception {
        ScheduledExecutorService s = Executors.newScheduledThreadPool(1);
        AtomicInteger calls = new AtomicInteger();
        RateLimiter rl = limiter(r -> calls.incrementAndGet() == 1
                ? new Fake(429, "{\"retry_after\":0.05,\"global\":false}", Map.of())
                : new Fake(200, "{\"id\":\"1\"}", Map.of()), s);
        PendingRequest req = new PendingRequest(Route.post("/channels/{}/messages", 1), null, null, null);
        rl.submit(req);
        JsonNode res = req.result.get(5, TimeUnit.SECONDS);
        assertEquals("1", res.get("id").asText());
        assertEquals(2, calls.get());
        s.shutdownNow();
    }

    @Test
    void mapsErrorsToDiscordApiException() throws Exception {
        ScheduledExecutorService s = Executors.newScheduledThreadPool(1);
        RateLimiter rl = limiter(r -> new Fake(403, "{\"code\":50013,\"message\":\"Missing Permissions\"}", Map.of()), s);
        PendingRequest req = new PendingRequest(Route.get("/channels/{}", 1), null, null, null);
        rl.submit(req);
        ExecutionException ex = assertThrows(ExecutionException.class, () -> req.result.get(5, TimeUnit.SECONDS));
        DiscordApiException d = (DiscordApiException) ex.getCause();
        assertEquals(403, d.status());
        assertEquals(50013, d.code());
        s.shutdownNow();
    }

    @Test
    void respectsExhaustedBucketUsingResetAfterHeader() throws Exception {
        ScheduledExecutorService s = Executors.newScheduledThreadPool(1);
        long[] times = new long[2];
        AtomicInteger n = new AtomicInteger();
        RateLimiter rl = limiter(r -> {
            times[n.getAndIncrement()] = System.currentTimeMillis();
            return new Fake(200, "{}", Map.of("x-ratelimit-bucket", List.of("abc"), "x-ratelimit-remaining", List.of("0"),
                    "x-ratelimit-reset-after", List.of("0.3")));
        }, s);
        PendingRequest a = new PendingRequest(Route.post("/channels/{}/messages", 9), null, null, null);
        PendingRequest b = new PendingRequest(Route.post("/channels/{}/messages", 9), null, null, null);
        rl.submit(a);
        rl.submit(b);
        a.result.get(5, TimeUnit.SECONDS);
        b.result.get(5, TimeUnit.SECONDS);
        assertTrue(times[1] - times[0] >= 250, "second request must wait for the bucket reset, waited " + (times[1] - times[0]));
        s.shutdownNow();
    }

    @Test
    void routeBuildsPathAndMajorParameter() {
        Route r = Route.get("/channels/{}/messages/{}", 10, 20).query("limit", 5);
        assertEquals("/channels/10/messages/20?limit=5", r.path());
        assertEquals("10", r.major());
        assertEquals("1:tok", Route.post("/webhooks/{}/{}", 1, "tok").major());
    }
}
