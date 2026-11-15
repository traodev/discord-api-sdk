package fr.traodev;

import com.fasterxml.jackson.databind.JsonNode;
import fr.traodev.command.CommandRegistry;
import fr.traodev.command.Router;
import fr.traodev.event.*;
import fr.traodev.gateway.ShardManager;
import fr.traodev.model.User;
import fr.traodev.rest.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class Bot implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(Bot.class);

    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledThreadPoolExecutor scheduler;
    private final RestClient rest;
    private final Api api;
    private final EventBus events;
    private final EventDispatcher dispatcher;
    private final CommandRegistry commands;
    private final ShardManager shards;
    private final Router<ButtonEvent> buttons = new Router<>();
    private final Router<SelectMenuEvent> selects = new Router<>();
    private final Router<ModalSubmitEvent> modals = new Router<>();
    private final Set<Long> guildIds = ConcurrentHashMap.newKeySet();
    private final boolean syncCommands;
    private final AtomicBoolean commandsSynced = new AtomicBoolean();
    private final AtomicBoolean started = new AtomicBoolean();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final CompletableFuture<Void> ready = new CompletableFuture<>();
    private final CompletableFuture<Void> terminated = new CompletableFuture<>();
    private volatile User self;
    private volatile long applicationId;

    Bot(String token, long intents, Integer shardCount, boolean compress, Presence presence, boolean syncCommands, boolean hook) {
        this.syncCommands = syncCommands;
        this.scheduler = new ScheduledThreadPoolExecutor(1, r -> {
            Thread t = new Thread(r, "jdisc-scheduler");
            t.setDaemon(true);
            return t;
        });
        this.scheduler.setRemoveOnCancelPolicy(true);
        this.rest = new RestClient(token, scheduler, executor);
        this.api = new Api(rest);
        this.events = new EventBus(executor);
        this.dispatcher = new EventDispatcher(this, events);
        this.commands = new CommandRegistry(this);
        this.shards = new ShardManager(this, token, intents, compress, shardCount, presence == null ? null : presence.toJson(), scheduler);

        events.register(ButtonEvent.class, e -> route(e, buttons.route(e.customId(), e), "button"));
        events.register(SelectMenuEvent.class, e -> route(e, selects.route(e.customId(), e), "select menu"));
        events.register(ModalSubmitEvent.class, e -> route(e, modals.route(e.customId(), e), "modal"));
        if (hook) Runtime.getRuntime().addShutdownHook(new Thread(this::close, "jdisc-shutdown"));
    }

    public static BotBuilder builder(String token) {
        return new BotBuilder(token);
    }

    private void route(InteractionEvent e, boolean handled, String what) {
        if (!handled) log.debug("No {} handler registered", what);
    }

    private <E extends InteractionEvent> Consumer<E> guarded(Consumer<E> h) {
        return e -> {
            try {
                h.accept(e);
            } catch (Throwable t) {
                commands.handleError(e, t);
            }
        };
    }

    public Bot start() {
        if (!started.compareAndSet(false, true)) throw new IllegalStateException("Bot already started");
        shards.start().exceptionally(t -> {
            fail(t instanceof CompletionException && t.getCause() != null ? t.getCause() : t);
            return null;
        });
        return this;
    }

    public Bot awaitReady() {
        try {
            ready.join();
        } catch (CompletionException e) {
            throw new IllegalStateException(e.getCause());
        }
        return this;
    }

    public void awaitShutdown() {
        try {
            terminated.join();
        } catch (CompletionException e) {
            throw new IllegalStateException(e.getCause());
        }
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        log.info("Shutting down");
        shards.close();
        scheduler.schedule(() -> {
            scheduler.shutdown();
            executor.shutdown();
            terminated.complete(null);
        }, 500, TimeUnit.MILLISECONDS);
    }

    public void fail(Throwable t) {
        log.error("Fatal error: {}", t.getMessage());
        ready.completeExceptionally(t);
        if (closed.compareAndSet(false, true)) {
            shards.close();
            scheduler.shutdown();
            executor.shutdown();
        }
        terminated.completeExceptionally(t);
    }

    public <E extends Event> Bot on(Class<E> type, Consumer<? super E> listener) {
        events.register(type, listener);
        return this;
    }

    public Bot onRaw(String eventName, Consumer<RawEvent> listener) {
        events.registerRaw(eventName, listener);
        return this;
    }

    public Bot onButton(String pattern, Consumer<ButtonEvent> handler) {
        buttons.register(pattern, guarded(handler));
        return this;
    }

    public Bot onSelectMenu(String pattern, Consumer<SelectMenuEvent> handler) {
        selects.register(pattern, guarded(handler));
        return this;
    }

    public Bot onModal(String pattern, Consumer<ModalSubmitEvent> handler) {
        modals.register(pattern, guarded(handler));
        return this;
    }

    public CommandRegistry commands() {
        return commands;
    }

    public Api api() {
        return api;
    }

    public RestClient rest() {
        return rest;
    }

    public EventBus events() {
        return events;
    }

    public ShardManager shards() {
        return shards;
    }

    public User selfUser() {
        return self;
    }

    public long selfId() {
        return self == null ? 0 : self.id();
    }

    public long applicationId() {
        return applicationId;
    }

    public Set<Long> guildIds() {
        return Set.copyOf(guildIds);
    }

    public long latencyMs() {
        return shards.latencyMs();
    }

    public CompletableFuture<Void> ready() {
        return ready;
    }

    public void setPresence(Presence p) {
        shards.setPresence(p.toJson());
    }

    public void handleReady(User self, long appId, java.util.Collection<Long> guilds) {
        this.self = self;
        this.applicationId = appId;
        guildIds.addAll(guilds);
        if (syncCommands && !commands.isEmpty() && commandsSynced.compareAndSet(false, true)) commands.sync();
    }

    public void handleAllShardsReady() {
        ready.complete(null);
    }

    public void handleDispatch(int shardId, String name, JsonNode d) {
        switch (name) {
            case "GUILD_CREATE" -> guildIds.add(Long.parseUnsignedLong(d.path("id").asText()));
            case "GUILD_DELETE" -> {
                if (!d.path("unavailable").asBoolean()) guildIds.remove(Long.parseUnsignedLong(d.path("id").asText()));
            }
            default -> {
            }
        }
        try {
            executor.execute(() -> dispatcher.dispatch(shardId, name, d));
        } catch (RejectedExecutionException ignored) {
        }
    }

    public void dispatch(Event e) {
        try {
            executor.execute(() -> events.publish(e));
        } catch (RejectedExecutionException ignored) {
        }
    }
}
