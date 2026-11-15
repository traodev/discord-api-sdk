package fr.traodev.rest;

import com.fasterxml.jackson.databind.JsonNode;
import fr.traodev.util.Json;
import fr.traodev.util.SlidingWindowLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.ArrayDeque;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

final class RateLimiter {
    private static final Logger log = LoggerFactory.getLogger(RateLimiter.class);
    private static final int MAX_ATTEMPTS = 5;

    private final ConcurrentHashMap<String, String> routeToHash = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final SlidingWindowLimiter global = new SlidingWindowLimiter(45, 1000);
    private volatile long globalBlockedUntil;
    private final ScheduledExecutorService scheduler;
    private final Executor executor;
    private final Function<PendingRequest, CompletableFuture<HttpResponse<byte[]>>> transport;

    RateLimiter(ScheduledExecutorService scheduler, Executor executor,
                Function<PendingRequest, CompletableFuture<HttpResponse<byte[]>>> transport) {
        this.scheduler = scheduler;
        this.executor = executor;
        this.transport = transport;
    }

    void submit(PendingRequest req) {
        String hash = routeToHash.get(req.route.templateKey());
        String key = hash == null ? req.route.bucketKey() : hash + ":" + req.route.major();
        buckets.computeIfAbsent(key, k -> new Bucket()).enqueue(req);
    }

    private final class Bucket {
        private final ArrayDeque<PendingRequest> queue = new ArrayDeque<>();
        private boolean running;
        private long remaining = 1;
        private long resetAtMs;

        synchronized void enqueue(PendingRequest r) {
            queue.addLast(r);
            if (!running) {
                running = true;
                executor.execute(this::drain);
            }
        }

        private synchronized void requeueFirst(PendingRequest r) {
            queue.addFirst(r);
        }

        void drain() {
            PendingRequest req;
            synchronized (this) {
                req = queue.peekFirst();
                if (req == null) {
                    running = false;
                    return;
                }
                long now = System.currentTimeMillis();
                long delay = 0;
                if (remaining <= 0) {
                    if (resetAtMs > now) delay = resetAtMs - now;
                    else remaining = 1;
                }
                delay = Math.max(delay, globalBlockedUntil - now);
                if (delay <= 0) delay = global.acquire();
                if (delay > 0) {
                    scheduler.schedule(() -> executor.execute(this::drain), delay, TimeUnit.MILLISECONDS);
                    return;
                }
                queue.pollFirst();
                remaining--;
            }
            final PendingRequest r = req;
            transport.apply(r).whenComplete((resp, err) -> onDone(r, resp, err));
        }

        private void onDone(PendingRequest req, HttpResponse<byte[]> resp, Throwable err) {
            try {
                handle(req, resp, err);
            } catch (Throwable t) {
                req.result.completeExceptionally(t);
            }
            executor.execute(this::drain);
        }

        private void handle(PendingRequest req, HttpResponse<byte[]> resp, Throwable err) {
            req.attempts++;
            if (err != null) {
                Throwable cause = err instanceof CompletionException && err.getCause() != null ? err.getCause() : err;
                if (req.attempts < 3) {
                    log.debug("Network error on {} {}, retrying: {}", req.route.method(), req.route.path(), cause.toString());
                    synchronized (this) {
                        resetAtMs = System.currentTimeMillis() + 500L * req.attempts;
                        remaining = 0;
                    }
                    requeueFirst(req);
                } else req.result.completeExceptionally(cause);
                return;
            }
            int status = resp.statusCode();
            updateLimits(req, resp.headers());
            if (status == 429) {
                JsonNode body = parse(resp.body());
                double retry = body.path("retry_after").asDouble(1.0);
                boolean isGlobal = body.path("global").asBoolean(false)
                    || "true".equalsIgnoreCase(resp.headers().firstValue("x-ratelimit-global").orElse(""));
                long until = System.currentTimeMillis() + (long) Math.ceil(retry * 1000);
                if (isGlobal) globalBlockedUntil = until;
                else synchronized (this) {
                    remaining = 0;
                    resetAtMs = until;
                }
                log.warn("Rate limited on {} {} (global={}), retry in {} ms", req.route.method(), req.route.path(), isGlobal, (long) (retry * 1000));
                if (req.attempts < MAX_ATTEMPTS) requeueFirst(req);
                else
                    req.result.completeExceptionally(new DiscordApiException(429, 0, "Rate limited too many times", null));
                return;
            }
            if (status >= 500 && req.attempts < 3) {
                synchronized (this) {
                    resetAtMs = System.currentTimeMillis() + 1000L * req.attempts;
                    remaining = 0;
                }
                requeueFirst(req);
                return;
            }
            if (status >= 200 && status < 300) {
                req.result.complete(parse(resp.body()));
            } else {
                JsonNode b = parse(resp.body());
                req.result.completeExceptionally(new DiscordApiException(status, b.path("code").asInt(0),
                    b.path("message").asText("Unknown error"), b.get("errors")));
            }
        }

        private void updateLimits(PendingRequest req, HttpHeaders h) {
            h.firstValue("x-ratelimit-bucket").ifPresent(b -> {
                routeToHash.putIfAbsent(req.route.templateKey(), b);
                buckets.putIfAbsent(b + ":" + req.route.major(), this);
            });
            h.firstValue("x-ratelimit-remaining").ifPresent(v -> {
                try {
                    synchronized (this) {
                        remaining = Long.parseLong(v);
                    }
                } catch (NumberFormatException ignored) {
                }
            });
            h.firstValue("x-ratelimit-reset-after").ifPresent(v -> {
                try {
                    synchronized (this) {
                        resetAtMs = System.currentTimeMillis() + (long) Math.ceil(Double.parseDouble(v) * 1000);
                    }
                } catch (NumberFormatException ignored) {
                }
            });
        }
    }

    private static JsonNode parse(byte[] body) {
        if (body == null || body.length == 0) return com.fasterxml.jackson.databind.node.NullNode.instance;
        try {
            return Json.parse(body);
        } catch (RuntimeException e) {
            return com.fasterxml.jackson.databind.node.NullNode.instance;
        }
    }
}
