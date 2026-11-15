# JDisc

JDisc is a Java library for writing Discord bots. It talks to version 10 of the Discord REST API and Gateway, and it relies on the HTTP client and WebSocket implementation that ship with the JDK. The only runtime dependencies are Jackson Databind and the SLF4J API.

It requires Java 21 or newer, because event handlers run on virtual threads.

## Status

This is version 0.1.0. The test suite exercises the gateway client against an in-process WebSocket server and the REST layer against an in-process HTTP server, but the library has not been run against Discord's production services in continuous integration. Expect rough edges and read the [limitations](#limitations) before depending on it.

## A small bot

```java
import fr.traodev.Bot;
import fr.traodev.command.Option;
import fr.traodev.command.SlashCommand;

public class Main {
    public static void main(String[] args) {
        Bot bot = Bot.builder(System.getenv("DISCORD_TOKEN")).build();

        bot.commands().register(
                SlashCommand.of("ping", "Show the gateway latency",
                        e -> e.reply("Pong, " + e.bot().latencyMs() + " ms")),
                SlashCommand.of("say", "Repeat a text",
                        e -> e.reply(e.string("text")),
                        Option.string("text", "What to say").required()));

        bot.start().awaitShutdown();
    }
}
```

Registered commands are sent to Discord once the first shard is ready. A larger example that covers autocomplete, buttons, modals and deferred replies is in [`examples/ExampleBot.java`](examples/ExampleBot.java).

## Installation

JDisc is not published to a public repository yet. Until it is, build it locally and depend on it from your own project:

```
./gradlew publishToMavenLocal
```

```kotlin
repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation("fr.traodev:jdisc:0.1.0-SNAPSHOT")
}
```

With Maven, the same coordinates apply: group `fr.traodev`, artifact `jdisc`, version `0.1.0-SNAPSHOT`.

SLF4J only provides the logging API. Add a binding such as `slf4j-simple` or Logback to see log output.

## Documentation

| Document | Contents |
| --- | --- |
| [Getting started](docs/getting-started.md) | Creating the application, intents, running a first bot, common startup errors |
| [Slash commands](docs/commands.md) | Options, subcommands, autocomplete, permissions, how commands are synchronized |
| [Interactions and components](docs/interactions.md) | Reply, defer and follow-up rules, buttons, select menus, modals, Components V2 |
| [Events](docs/events.md) | Listening to events, the event list with required intents, threading model |
| [REST](docs/rest.md) | The `Api` helper, the raw client, rate limiting, errors, file uploads |
| [Architecture](docs/architecture.md) | How the gateway, rate limiter and dispatcher work, and how they are tested |

## How it behaves

Handlers are called on virtual threads, one task per event. Blocking inside a handler, for example by calling `join()` on a future, does not hold up the gateway connection. The price is that events are not delivered in order relative to each other.

Gateway payloads are parsed into a JSON tree on the connection thread, but they are only converted into event objects when something is listening for that event type. A bot that only uses slash commands never pays for decoding messages or member updates.

REST requests go through a rate limiter that tracks Discord's buckets, honours the global limit, and retries on 429 and transient 5xx responses. Gateway connections use zlib-stream compression by default, answer heartbeats, detect dead connections, and resume their session after a drop when Discord allows it.

## Project layout

```
fr.traodev            Bot, BotBuilder, Api, Intent, Permission, Presence, Activity
fr.traodev.command    SlashCommand, ContextCommand, Option, CommandRegistry, Router
fr.traodev.component  MessageBuilder, EmbedBuilder, Component, Modal
fr.traodev.event      Event types, EventBus, EventDispatcher
fr.traodev.gateway    GatewayShard, ShardManager, ZlibStreamDecoder
fr.traodev.model      Immutable records for Discord objects
fr.traodev.rest       RestClient, RateLimiter, Route, FileUpload, DiscordApiException
fr.traodev.util       Json, Snowflake, SlidingWindowLimiter
```

## Building and testing

```
./gradlew build
```

The tests include a minimal WebSocket server that plays the gateway side of the handshake (HELLO, IDENTIFY, READY, heartbeats, RECONNECT, RESUME, with zlib-stream framing) and a local HTTP server that records the REST calls produced by interaction handlers.

## Limitations

- There is no voice support.
- The only state kept is the bot's own user, application id and the set of guild ids. Guilds, channels, members and roles are not cached, so fetch them through `Api` or listen to the matching events.
- Gateway payloads are JSON only. ETF is not implemented.
- `Api` covers the endpoints most bots use. Anything else is reachable through `bot.rest()`, which still gets rate limiting and retries, but you build the route and body yourself.
- Handlers for one interaction or message are not serialized. If you need ordering per channel, do it in your own code.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Notable changes are listed in [CHANGELOG.md](CHANGELOG.md).

## License

MIT. See [LICENSE](LICENSE).
