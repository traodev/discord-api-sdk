package fr.traodev.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.traodev.util.Json;
import fr.traodev.util.SlidingWindowLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public final class GatewayShard {
    private static final Logger log = LoggerFactory.getLogger(GatewayShard.class);

    private final ShardManager manager;
    private final HttpClient http;
    private final int shardId;
    private final int shardCount;
    private final String gatewayUrl;

    private volatile WebSocket ws;
    private volatile int generation;
    private volatile String sessionId;
    private volatile String resumeUrl;
    private volatile long seq = -1;
    private volatile boolean ack = true;
    private volatile boolean resuming;
    private volatile boolean closing;
    private volatile long lastBeat;
    private volatile long latencyMs = -1;
    private volatile ScheduledFuture<?> heartbeat;
    private int attempts;
    private ZlibStreamDecoder inflater;
    private final StringBuilder textBuf = new StringBuilder();
    private CompletableFuture<?> sendChain = CompletableFuture.completedFuture(null);
    private final SlidingWindowLimiter sendLimiter = new SlidingWindowLimiter(110, 60_000);

    GatewayShard(ShardManager manager, HttpClient http, int shardId, int shardCount, String gatewayUrl) {
        this.manager = manager;
        this.http = http;
        this.shardId = shardId;
        this.shardCount = shardCount;
        this.gatewayUrl = gatewayUrl;
    }

    public int shardId() {
        return shardId;
    }

    public long latencyMs() {
        return latencyMs;
    }

    public boolean isConnected() {
        return ws != null && !ws.isInputClosed() && !ws.isOutputClosed();
    }

    void start() {
        connect(false);
    }

    private void connect(boolean tryResume) {
        if (closing) return;
        resuming = tryResume && sessionId != null;
        String base = resuming ? resumeUrl : gatewayUrl;
        String url = base + (base.contains("?") ? "&" : "?") + "v=10&encoding=json" + (manager.compress() ? "&compress=zlib-stream" : "");
        int gen = ++generation;
        if (inflater != null) inflater.close();
        inflater = manager.compress() ? new ZlibStreamDecoder() : null;
        textBuf.setLength(0);
        log.debug("[shard {}] connecting ({})", shardId, resuming ? "resume" : "identify");
        http.newWebSocketBuilder().connectTimeout(Duration.ofSeconds(15))
            .buildAsync(URI.create(url), new Listener(gen))
            .whenComplete((w, err) -> {
                if (err != null && gen == generation) {
                    log.warn("[shard {}] connection failed: {}", shardId, err.toString());
                    scheduleReconnect(resuming, 0);
                } else if (err == null) ws = w;
            });
    }

    private final class Listener implements WebSocket.Listener {
        private final int gen;

        Listener(int gen) {
            this.gen = gen;
        }

        @Override
        public void onOpen(WebSocket w) {
            ws = w;
            w.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket w, CharSequence data, boolean last) {
            textBuf.append(data);
            if (last) {
                String s = textBuf.toString();
                textBuf.setLength(0);
                if (gen == generation) handle(Json.parse(s));
            }
            w.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onBinary(WebSocket w, ByteBuffer data, boolean last) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            try {
                if (inflater != null && gen == generation) {
                    byte[] msg = inflater.feed(bytes);
                    if (msg != null) handle(Json.parse(msg));
                }
            } catch (Exception e) {
                log.error("[shard {}] decompression error, reconnecting", shardId, e);
                forceReconnect(gen, true, 0);
            }
            w.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket w, int code, String reason) {
            closed(gen, code, reason);
            return null;
        }

        @Override
        public void onError(WebSocket w, Throwable error) {
            log.warn("[shard {}] websocket error: {}", shardId, error.toString());
            closed(gen, 1006, String.valueOf(error.getMessage()));
        }
    }

    private void handle(JsonNode p) {
        int op = p.path("op").asInt(-1);
        switch (op) {
            case 0 -> {
                if (p.hasNonNull("s")) seq = p.get("s").asLong();
                String t = p.path("t").asText();
                JsonNode d = p.get("d");
                switch (t) {
                    case "READY" -> {
                        sessionId = d.path("session_id").asText();
                        resumeUrl = d.path("resume_gateway_url").asText(gatewayUrl);
                        attempts = 0;
                        manager.onReady(this, d);
                    }
                    case "RESUMED" -> {
                        attempts = 0;
                        manager.onResumed(this);
                    }
                    default -> manager.onDispatch(this, t, d);
                }
            }
            case 1 -> beat();
            case 7 -> {
                log.debug("[shard {}] RECONNECT requested", shardId);
                forceReconnect(generation, true, 0);
            }
            case 9 -> {
                boolean canResume = p.path("d").asBoolean(false);
                log.debug("[shard {}] INVALID_SESSION (resumable={})", shardId, canResume);
                if (!canResume) sessionId = null;
                forceReconnect(generation, canResume, 1000 + ThreadLocalRandom.current().nextInt(4000));
            }
            case 10 -> onHello(p.get("d").path("heartbeat_interval").asLong(41250));
            case 11 -> {
                ack = true;
                latencyMs = System.currentTimeMillis() - lastBeat;
            }
            default -> {
            }
        }
    }

    private void onHello(long interval) {
        stopHeartbeat();
        ack = true;
        long first = (long) (interval * ThreadLocalRandom.current().nextDouble());
        heartbeat = manager.scheduler().scheduleAtFixedRate(this::beat, first, interval, TimeUnit.MILLISECONDS);
        if (resuming) {
            ObjectNode d = Json.object().put("token", manager.token()).put("session_id", sessionId).put("seq", seq);
            sendRaw(Json.object().put("op", 6).set("d", d).toString());
        } else {
            seq = -1;
            final int gen = generation;
            manager.acquireIdentify(shardId).thenRun(() -> {
                if (gen == generation) identify();
            });
        }
    }

    private void identify() {
        ObjectNode props = Json.object().put("os", System.getProperty("os.name", "java").toLowerCase())
            .put("browser", "jdisc").put("device", "jdisc");
        ObjectNode d = Json.object().put("token", manager.token()).put("intents", manager.intents());
        d.set("properties", props);
        d.put("large_threshold", 250);
        d.putArray("shard").add(shardId).add(shardCount);
        if (manager.presence() != null) d.set("presence", manager.presence());
        sendRaw(Json.object().put("op", 2).set("d", d).toString());
    }

    private void beat() {
        if (!ack) {
            log.warn("[shard {}] no heartbeat ACK (zombie connection), reconnecting", shardId);
            forceReconnect(generation, true, 0);
            return;
        }
        ack = false;
        lastBeat = System.currentTimeMillis();
        ObjectNode n = Json.object().put("op", 1);
        if (seq >= 0) n.put("d", seq);
        else n.putNull("d");
        sendRaw(n.toString());
    }

    private void stopHeartbeat() {
        ScheduledFuture<?> h = heartbeat;
        if (h != null) h.cancel(false);
    }

    public void send(ObjectNode payload) {
        sendLimited(payload.toString());
    }

    private void sendLimited(String text) {
        long wait = sendLimiter.acquire();
        if (wait > 0) manager.scheduler().schedule(() -> sendLimited(text), wait, TimeUnit.MILLISECONDS);
        else sendRaw(text);
    }

    private synchronized void sendRaw(String text) {
        WebSocket w = ws;
        if (w == null) return;
        sendChain = sendChain.handle((v, e) -> null).thenCompose(v -> w.sendText(text, true))
            .exceptionally(e -> {
                log.debug("[shard {}] send failed: {}", shardId, e.toString());
                return null;
            });
    }

    private void closed(int gen, int code, String reason) {
        if (gen != generation || closing) return;
        stopHeartbeat();
        boolean fatal = switch (code) {
            case 4004, 4010, 4011, 4012, 4013, 4014 -> true;
            default -> false;
        };
        boolean canResume = sessionId != null && code != 4007 && code != 4009 && code != 1000 && code != 1001;
        if (!canResume) sessionId = null;
        log.info("[shard {}] closed: {} {} (fatal={}, resume={})", shardId, code, reason, fatal, canResume);
        manager.onDisconnect(this, code, reason, !fatal);
        if (fatal) {
            closing = true;
            return;
        }
        scheduleReconnect(canResume, 0);
    }

    private void forceReconnect(int gen, boolean resume, long extraDelayMs) {
        if (gen != generation || closing) return;
        generation++;
        stopHeartbeat();
        WebSocket w = ws;
        if (w != null) w.abort();
        if (!resume) sessionId = null;
        scheduleReconnect(resume && sessionId != null, extraDelayMs);
    }

    private void scheduleReconnect(boolean resume, long extraDelayMs) {
        long backoff = Math.min(30_000L, 1000L << Math.min(attempts++, 5));
        long delay = extraDelayMs + (attempts == 1 && extraDelayMs == 0 ? 0 : backoff) + ThreadLocalRandom.current().nextLong(500);
        manager.scheduler().schedule(() -> connect(resume), delay, TimeUnit.MILLISECONDS);
    }

    void close() {
        closing = true;
        stopHeartbeat();
        WebSocket w = ws;
        if (w != null) w.sendClose(1000, "bye").whenComplete((x, e) -> w.abort());
        if (inflater != null) inflater.close();
    }
}
