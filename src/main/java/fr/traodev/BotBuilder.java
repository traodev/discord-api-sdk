package fr.traodev;

import java.util.Arrays;
import java.util.EnumSet;

public final class BotBuilder {
    private final String token;
    private final EnumSet<Intent> intents = EnumSet.of(Intent.GUILDS);
    private Integer shards;
    private boolean compress = true;
    private boolean syncCommands = true;
    private boolean shutdownHook = true;
    private Presence presence;

    BotBuilder(String token) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("Bot token is empty");
        String t = token.trim();
        this.token = t.regionMatches(true, 0, "Bot ", 0, 4) ? t.substring(4).trim() : t;
    }

    public BotBuilder intents(Intent... intents) {
        this.intents.clear();
        this.intents.addAll(Arrays.asList(intents));
        return this;
    }

    public BotBuilder addIntents(Intent... more) {
        intents.addAll(Arrays.asList(more));
        return this;
    }

    public BotBuilder shards(int count) {
        this.shards = count;
        return this;
    }

    public BotBuilder compress(boolean c) {
        this.compress = c;
        return this;
    }

    public BotBuilder presence(Presence p) {
        this.presence = p;
        return this;
    }

    public BotBuilder syncCommands(boolean s) {
        this.syncCommands = s;
        return this;
    }

    public BotBuilder shutdownHook(boolean s) {
        this.shutdownHook = s;
        return this;
    }

    public Bot build() {
        return new Bot(token, Intent.mask(intents), shards, compress, presence, syncCommands, shutdownHook);
    }

    public Bot start() {
        return build().start();
    }
}
