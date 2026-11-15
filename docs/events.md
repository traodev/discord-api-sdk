# Events

## Listening

```java
bot.on(MessageCreateEvent.class, e -> {
    System.out.println(e.author().tag() + ": " + e.content());
});
```

`on` can be called any number of times for the same type, and listeners are called in no particular order. Every listener call is its own task on a virtual thread. An exception thrown by a listener is logged and does not affect other listeners or the connection.

Listening to a superclass receives all of its subclasses. `InteractionEvent.class` receives every interaction event, and `Event.class` receives everything. Be aware that a listener on `Event.class` turns off the decoding optimization described below.

## Event list

| Event | Gateway event | Intent required |
| --- | --- | --- |
| `ReadyEvent` | READY | none |
| `ResumedEvent` | RESUMED | none |
| `ShardDisconnectEvent` | (raised by JDisc when a connection closes) | none |
| `GuildCreateEvent` | GUILD_CREATE | `GUILDS` |
| `GuildUpdateEvent` | GUILD_UPDATE | `GUILDS` |
| `GuildDeleteEvent` | GUILD_DELETE | `GUILDS` |
| `ChannelCreateEvent`, `ChannelUpdateEvent`, `ChannelDeleteEvent` | CHANNEL_CREATE, CHANNEL_UPDATE, CHANNEL_DELETE | `GUILDS` |
| `MemberJoinEvent`, `MemberUpdateEvent`, `MemberLeaveEvent` | GUILD_MEMBER_ADD, GUILD_MEMBER_UPDATE, GUILD_MEMBER_REMOVE | `GUILD_MEMBERS` (privileged) |
| `MessageCreateEvent`, `MessageUpdateEvent`, `MessageDeleteEvent` | MESSAGE_CREATE, MESSAGE_UPDATE, MESSAGE_DELETE | `GUILD_MESSAGES` and/or `DIRECT_MESSAGES` |
| `ReactionAddEvent`, `ReactionRemoveEvent` | MESSAGE_REACTION_ADD, MESSAGE_REACTION_REMOVE | `GUILD_MESSAGE_REACTIONS` and/or `DIRECT_MESSAGE_REACTIONS` |
| `SlashCommandEvent`, `ContextMenuEvent`, `ButtonEvent`, `SelectMenuEvent`, `ModalSubmitEvent`, `AutocompleteEvent` | INTERACTION_CREATE | none |
| `RawEvent` | any | depends on the event |

A few details worth knowing:

- `ReadyEvent` fires once per shard. Guilds then arrive one by one as `GuildCreateEvent`, including at startup, so a bot in many guilds sees a burst of them.
- `MessageUpdateEvent` often carries only the changed fields. Fields that were not sent show up as empty strings, empty lists, zero or null.
- `MessageCreateEvent.content()` is empty without the `MESSAGE_CONTENT` intent, apart from direct messages and messages that mention the bot.
- `GuildDeleteEvent.unavailable()` is true for an outage and false when the bot was removed from the guild.

## Raw events

For events without a typed class, or when you want the payload untouched, listen by gateway event name:

```java
bot.onRaw("TYPING_START", raw -> {
    long userId = raw.data().path("user_id").asLong();
});
```

`raw.data()` is the Jackson tree of the `d` field. Raw listeners also work for events that do have a typed class, and both kinds can be used at once.

## Decoding only when needed

The connection thread parses each payload into a tree and hands it to a worker. The worker converts it into an event object only if somebody registered a listener for that type. If nothing listens for `MemberUpdateEvent`, member updates cost a tree parse and nothing more. If your bot is only interested in a handful of events, also keep your intents minimal so Discord does not send the rest at all.

## State available without events

The bot keeps a few values up to date from the gateway:

```java
bot.selfUser();    // the bot's own user
bot.applicationId();
bot.guildIds();    // snapshot of the guilds the bot is in
bot.latencyMs();   // average heartbeat round trip across shards, -1 until known
```

Nothing else is cached. Fetch guilds, channels and members through [`Api`](rest.md), or build your own cache from the events.

## Ordering

Because every listener call is a separate task, two events that arrive one after the other may be processed in either order. For most bots this does not matter. If it matters for you, for example when applying a series of edits to one message, queue the work yourself, keyed by channel or guild, inside the listener.
