# Getting started

## Create the application

1. Open the Discord Developer Portal and create a new application.
2. In the Bot section, create the bot user and copy its token. Treat the token like a password. Anyone who has it can act as your bot.
3. If you need privileged intents (see below), enable them on the same page.
4. In the OAuth2 section, generate an invite URL with the `bot` and `applications.commands` scopes and add the bot to a test server.

Pass the token to your program through an environment variable. Do not commit it.

## Add the dependency

Add JDisc as described in the [README](../README.md#installation), plus a logging backend. With Gradle:

```kotlin
dependencies {
    implementation("fr.traodev:jdisc:0.1.0-SNAPSHOT")
    runtimeOnly("org.slf4j:slf4j-simple:2.0.13")
}
```

JDisc needs Java 21, so set your project's release level accordingly, for example with `tasks.withType<JavaCompile> { options.release.set(21) }`.

## Choose your intents

Intents decide which events Discord sends to your bot. JDisc connects with `GUILDS` only unless you say otherwise, which is enough for a bot that responds to slash commands.

```java
Bot bot = Bot.builder(token)
        .addIntents(Intent.GUILD_MESSAGES, Intent.MESSAGE_CONTENT)
        .build();
```

`addIntents` adds to the default set, `intents` replaces it.

Three intents are privileged: `GUILD_MEMBERS`, `GUILD_PRESENCES` and `MESSAGE_CONTENT`. They must be switched on in the Developer Portal, and bots in many servers need approval from Discord. If you request one without enabling it, the gateway closes the connection with code 4014 and `awaitShutdown()` throws.

Without `MESSAGE_CONTENT`, the `content` field of messages is empty, except in direct messages, in messages that mention the bot, and in the bot's own messages.

## Write and run a bot

```java
import fr.traodev.Bot;
import fr.traodev.Intent;
import fr.traodev.event.MessageCreateEvent;

public class Main {
    public static void main(String[] args) {
        Bot bot = Bot.builder(System.getenv("DISCORD_TOKEN"))
                .addIntents(Intent.GUILD_MESSAGES, Intent.MESSAGE_CONTENT)
                .build();

        bot.on(MessageCreateEvent.class, e -> {
            if (!e.author().bot() && e.content().equals("!ping")) {
                e.reply("pong");
            }
        });

        bot.start().awaitShutdown();
    }
}
```

`start()` returns immediately after beginning the connection. `awaitReady()` blocks until every shard is connected. `awaitShutdown()` blocks until the bot is closed, either by Ctrl+C, by calling `close()`, or because of a fatal error such as an invalid token. In the last case it throws an `IllegalStateException` carrying the reason.

A JVM shutdown hook that closes the bot cleanly is installed by default. Disable it with `.shutdownHook(false)` if your application manages its own lifecycle.

## Presence

```java
Bot bot = Bot.builder(token)
        .presence(Presence.online(Activity.watching("the logs")))
        .build();

bot.setPresence(Presence.dnd(Activity.playing("maintenance")));
```

## Common startup errors

| Symptom | Cause |
| --- | --- |
| Fatal close 4004 | The token is wrong or was reset. |
| Fatal close 4013 | The intents value is invalid. |
| Fatal close 4014 | You asked for a privileged intent that is not enabled in the Developer Portal. |
| Fatal close 4011 | The bot is in too many guilds for a single shard. Set the shard count explicitly or leave it unset so JDisc uses Discord's recommendation. |
| Startup fails with "Not enough session starts left" | Discord limits how many times a token can identify per day. Wait for the reset reported by the `/gateway/bot` endpoint. |
| Slash commands do not appear | Global commands can take a while to show up. While developing, register them with `.inGuild(guildId)` for immediate updates, and make sure the bot was invited with the `applications.commands` scope. |
