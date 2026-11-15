package fr.traodev.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import fr.traodev.Bot;
import fr.traodev.event.ReadyEvent;
import fr.traodev.event.ResumedEvent;
import fr.traodev.event.ShardDisconnectEvent;
import fr.traodev.model.User;
import fr.traodev.rest.Route;
import fr.traodev.util.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class ShardManager {
    private static final Logger log = LoggerFactory.getLogger(ShardManager.class);

    private final Bot bot;
    private final String token;
    private final long intents;
    private final boolean compress;
    private final Integer requestedShards;
    private final ScheduledExecutorService scheduler;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
    private final List<GatewayShard> shards = new ArrayList<>();
    private final AtomicInteger readyShards = new AtomicInteger();
    private volatile ObjectNode presence;
    private long[] nextIdentify = new long[1];
    private int maxConcurrency = 1;

    public ShardManager(Bot bot, String token, long intents, boolean compress, Integer shards, ObjectNode presence,
                        ScheduledExecutorService scheduler) {
        this.bot = bot;
        this.token = token;
        this.intents = intents;
        this.compress = compress;
        this.requestedShards = shards;
        this.presence = presence;
        this.scheduler = scheduler;
    }

    String token() {
        return token;
    }

    long intents() {
        return intents;
    }

    boolean compress() {
        return compress;
    }

    ObjectNode presence() {
        return presence;
    }

    ScheduledExecutorService scheduler() {
        return scheduler;
    }

    public List<GatewayShard> shards() {
        return List.copyOf(shards);
    }

    public int shardCount() {
        return shards.size();
    }

    public long latencyMs() {
        return (long) shards.stream().mapToLong(GatewayShard::latencyMs).filter(l -> l >= 0).average().orElse(-1);
    }

    public CompletableFuture<Void> start() {
        return bot.rest().request(Route.get("/gateway/bot")).thenAccept(info -> {
            String url = info.path("url").asText("wss://gateway.discord.gg");
            int count = requestedShards != null ? requestedShards : Math.max(1, info.path("shards").asInt(1));
            maxConcurrency = Math.max(1, info.path("session_start_limit").path("max_concurrency").asInt(1));
            int remaining = info.path("session_start_limit").path("remaining").asInt(1000);
            if (remaining < count)
                throw new IllegalStateException("Not enough session starts left (" + remaining + ") for " + count + " shard(s)");
            nextIdentify = new long[maxConcurrency];
            log.info("Starting {} shard(s), max_concurrency={}", count, maxConcurrency);
            for (int i = 0; i < count; i++) {
                GatewayShard s = new GatewayShard(this, http, i, count, url);
                shards.add(s);
            }
            shards.forEach(GatewayShard::start);
        });
    }

    CompletableFuture<Void> acquireIdentify(int shardId) {
        long delay;
        synchronized (this) {
            int key = shardId % maxConcurrency;
            long now = System.currentTimeMillis();
            delay = Math.max(0, nextIdentify[key] - now);
            nextIdentify[key] = now + delay + 5_100;
        }
        if (delay == 0) return CompletableFuture.completedFuture(null);
        CompletableFuture<Void> f = new CompletableFuture<>();
        scheduler.schedule(() -> f.complete(null), delay, TimeUnit.MILLISECONDS);
        return f;
    }

    void onReady(GatewayShard shard, JsonNode d) {
        User self = Json.convert(d.get("user"), User.class);
        long appId = Long.parseUnsignedLong(d.path("application").path("id").asText("0"));
        List<Long> guildIds = new ArrayList<>();
        d.path("guilds").forEach(g -> guildIds.add(Long.parseUnsignedLong(g.path("id").asText())));
        bot.handleReady(self, appId, guildIds);
        log.info("[shard {}] ready as {} ({} guilds)", shard.shardId(), self.tag(), guildIds.size());
        bot.dispatch(new ReadyEvent(bot, shard.shardId(), self, guildIds.size(), d.path("session_id").asText()));
        if (readyShards.incrementAndGet() == shards.size()) bot.handleAllShardsReady();
    }

    void onResumed(GatewayShard shard) {
        bot.dispatch(new ResumedEvent(bot, shard.shardId()));
    }

    void onDispatch(GatewayShard shard, String name, JsonNode d) {
        bot.handleDispatch(shard.shardId(), name, d);
    }

    void onDisconnect(GatewayShard shard, int code, String reason, boolean willReconnect) {
        bot.dispatch(new ShardDisconnectEvent(bot, shard.shardId(), code, reason, willReconnect));
        if (!willReconnect)
            bot.fail(new IllegalStateException("Fatal gateway close " + code + ": " + reason + hint(code)));
    }

    private static String hint(int code) {
        return switch (code) {
            case 4004 -> " (invalid token)";
            case 4013 -> " (invalid intents)";
            case 4014 -> " (disallowed intents: enable privileged intents in the Developer Portal)";
            case 4011 -> " (sharding required)";
            default -> "";
        };
    }

    public void setPresence(ObjectNode presenceJson) {
        this.presence = presenceJson;
        ObjectNode payload = Json.object().put("op", 3);
        payload.set("d", presenceJson);
        shards.forEach(s -> s.send(payload));
    }

    public void close() {
        shards.forEach(GatewayShard::close);
    }
}
