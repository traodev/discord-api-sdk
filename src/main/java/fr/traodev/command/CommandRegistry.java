package fr.traodev.command;

import com.fasterxml.jackson.databind.node.ArrayNode;
import fr.traodev.Bot;
import fr.traodev.event.*;
import fr.traodev.rest.Route;
import fr.traodev.util.Json;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

public final class CommandRegistry {
    private static final Logger log = LoggerFactory.getLogger(CommandRegistry.class);

    private final Bot bot;
    private final List<SlashCommand> slash = new ArrayList<>();
    private final List<ContextCommand> context = new ArrayList<>();
    private final Map<String, Consumer<SlashCommandEvent>> slashHandlers = new ConcurrentHashMap<>();
    private final Map<String, Consumer<AutocompleteEvent>> autocompleteHandlers = new ConcurrentHashMap<>();
    private final Map<String, Consumer<ContextMenuEvent>> contextHandlers = new ConcurrentHashMap<>();
    private volatile BiConsumer<InteractionEvent, Throwable> errorHandler = (e, t) -> {
        log.error("Interaction handler failed", t);
        if (!e.isAcknowledged()) e.replyEphemeral("An error occurred while running this command.");
    };

    public CommandRegistry(Bot bot) {
        this.bot = bot;
        bot.events().register(SlashCommandEvent.class, e -> {
            Consumer<SlashCommandEvent> h = slashHandlers.get(e.path());
            if (h == null) {
                log.warn("No handler for /{}", e.path());
                return;
            }
            guard(e, () -> h.accept(e));
        });
        bot.events().register(ContextMenuEvent.class, e -> {
            Consumer<ContextMenuEvent> h = contextHandlers.get(e.name());
            if (h != null) guard(e, () -> h.accept(e));
        });
        bot.events().register(AutocompleteEvent.class, e -> {
            Consumer<AutocompleteEvent> h = autocompleteHandlers.get(e.commandPath() + "/" + e.focusedName());
            if (h != null) guard(e, () -> h.accept(e));
        });
    }

    private void guard(InteractionEvent e, Runnable r) {
        try {
            r.run();
        } catch (Throwable t) {
            handleError(e, t);
        }
    }

    public void handleError(InteractionEvent e, Throwable t) {
        try {
            errorHandler.accept(e, t);
        } catch (Throwable ignored) {
        }
    }

    public CommandRegistry onError(BiConsumer<InteractionEvent, Throwable> handler) {
        this.errorHandler = handler;
        return this;
    }

    public CommandRegistry register(SlashCommand... commands) {
        for (SlashCommand c : commands) {
            slash.add(c);
            slashHandlers.putAll(c.handlers());
            autocompleteHandlers.putAll(c.autocompletes());
        }
        return this;
    }

    public CommandRegistry register(ContextCommand... commands) {
        for (ContextCommand c : commands) {
            context.add(c);
            contextHandlers.put(c.name(), c.handler());
        }
        return this;
    }

    public CompletableFuture<Void> sync() {
        Map<Long, ArrayNode> byScope = new TreeMap<>();

        for (SlashCommand c : slash) byScope.computeIfAbsent(c.guildId(), k -> Json.array()).add(c.toJson());
        for (ContextCommand c : context) byScope.computeIfAbsent(c.guildId(), k -> Json.array()).add(c.toJson());

        List<CompletableFuture<?>> all = new ArrayList<>();
        long app = bot.applicationId();
        byScope.forEach((guild, arr) -> {
            Route r = guild == 0 ? Route.put("/applications/{}/commands", app) : Route.put("/applications/{}/guilds/{}/commands", app, guild);
            all.add(bot.rest().request(r, arr).whenComplete((res, err) -> {
                if (err != null) log.error("Command sync failed for scope {}", guild == 0 ? "global" : guild, err);
                else
                    log.info("Synced {} command(s) for scope {}", arr.size(), guild == 0 ? "global" : "guild " + guild);
            }));
        });
        return CompletableFuture.allOf(all.toArray(CompletableFuture[]::new));
    }

    public boolean isEmpty() {
        return slash.isEmpty() && context.isEmpty();
    }
}
