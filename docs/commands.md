# Slash commands

## Defining a command

A command is a name, a description, an optional list of options and a handler.

```java
SlashCommand.of("greet", "Greet someone",
        e -> e.reply("Hello, " + e.string("name", "world")),
        Option.string("name", "Who to greet"));
```

Register it on the bot:

```java
bot.commands().register(command1, command2);
```

Names must be lowercase with no spaces, as Discord requires. Descriptions are limited to 100 characters.

## Options

`Option` has one factory per Discord option type: `string`, `integer`, `number`, `bool`, `user`, `channel`, `role`, `mentionable` and `attachment`. Modifiers return the option so they can be chained.

```java
Option.integer("amount", "How many").required().min(1).max(100)
Option.string("mode", "Mode").choices("fast", "slow")
Option.string("reason", "Why").maxLength(200)
Option.channel("target", "Where").channelTypes(Channel.GUILD_TEXT)
```

Use `choice(name, value)` when the displayed label should differ from the value. `choices` and autocomplete cannot be combined on one option.

Inside a handler, read options by name:

| Method | Returns |
| --- | --- |
| `string(name)`, `string(name, default)` | `String` |
| `integer(name)`, `integer(name, default)` | `Long` / `long` |
| `number(name)` | `Double` |
| `bool(name)`, `bool(name, default)` | `Boolean` / `boolean` |
| `user(name)`, `member(name)`, `role(name)`, `channel(name)`, `attachment(name)` | resolved objects sent by Discord |
| `id(name)` | the raw snowflake of a user, channel, role or mentionable option |
| `has(name)` | whether the option was supplied |

The object-returning accessors give `null` when the option was not supplied.

## Subcommands and groups

```java
SlashCommand.of("economy", "Manage the economy")
        .sub("balance", "Show a balance",
                e -> e.reply("..."),
                Option.user("user", "Whose balance"))
        .group("admin", "Administration", g -> g
                .sub("give", "Give coins",
                        e -> e.reply("..."),
                        Option.integer("amount", "How many").required()));
```

This produces `/economy balance` and `/economy admin give`. A command that has subcommands cannot also have a top-level handler on Discord's side, so the handler passed to `of` is only used when there are no subcommands. In the event, `subcommand()` and `subcommandGroup()` tell you what was invoked, and the option accessors read from the subcommand's own options.

Discord allows one level of grouping, and limits a command to 25 options or subcommands.

## Autocomplete

```java
Option.string("color", "Pick a color").required()
        .autocomplete(ac -> ac.suggest(
                COLORS.stream()
                        .filter(c -> c.startsWith(ac.focusedValue().toLowerCase()))
                        .toList()));
```

`suggest` accepts a collection of strings or a map from display name to value. At most 25 suggestions are sent. Autocomplete must be answered within three seconds and cannot be deferred.

## Permissions and visibility

```java
SlashCommand.of("purge", "Delete messages", handler)
        .requirePermissions(Permission.MANAGE_MESSAGES)
        .guildOnly();
```

`requirePermissions` sets the default member permissions. Server administrators can override this in their server settings, so it is a default and not an access check. Verify permissions in the handler for anything sensitive, using `e.member().hasPermission(...)`.

`nsfw()` marks the command as age-restricted. `localizeName` and `localizeDescription` add translations for a locale such as `fr`.

## Context menu commands

User and message commands appear in the right-click menu and take no options.

```java
bot.commands().register(
        ContextCommand.user("Show avatar", e -> e.reply(e.targetUser().avatarUrl())),
        ContextCommand.message("Quote", e -> e.reply("> " + e.targetMessage().content())));
```

## Global and guild commands

By default a command is global. Global changes can take time to propagate to every client. A command registered with `inGuild(id)` exists only in that guild and updates at once, which is what you want during development.

```java
SlashCommand.of("ping", "Ping", handler).inGuild(123456789012345678L)
```

## Synchronization

After the first `READY` event, JDisc sends your definitions to Discord with the bulk overwrite endpoint, once per scope (global, and each guild you used). Bulk overwrite replaces everything in that scope, so commands you stop registering are removed from Discord the next time you start the bot.

Two details matter in practice:

- A scope with no registered commands is left alone. Registering nothing does not wipe your global commands.
- Synchronization runs once per process start. Disable it with `.syncCommands(false)` on the builder and call `bot.commands().sync()` yourself if you want to control when it happens, for example to avoid pushing the same definitions on every restart.

## Errors in handlers

If a handler throws, JDisc logs the exception and, when the interaction has not been answered yet, replies with an ephemeral error message. Replace this behaviour with your own:

```java
bot.commands().onError((event, error) -> {
    log.error("Command failed", error);
    if (!event.isAcknowledged()) {
        event.replyEphemeral("Something went wrong.");
    }
});
```

The same handler is used for button, select menu and modal handlers registered through the bot.
