# Interactions and components

Slash commands, context menu commands, buttons, select menus, modals and autocomplete requests are all interactions. Each one arrives as a subclass of `InteractionEvent`, and each one must be answered within three seconds.

## Answering

`InteractionEvent` keeps track of what has been sent so far, and `reply` picks the right Discord call:

| State when `reply` is called | What happens |
| --- | --- |
| Nothing sent yet | The initial response is sent. |
| After `defer()` | The deferred response is replaced with your message. |
| Already answered | A follow-up message is sent. |

```java
e.reply("First answer");
e.reply("This goes out as a follow-up");
```

Other methods on the event:

| Method | Purpose |
| --- | --- |
| `replyEphemeral(text)` | Reply visible only to the invoking user. |
| `defer()`, `defer(ephemeral)` | Acknowledge now and answer later. The interaction token stays valid for 15 minutes. |
| `edit(message)` | Edit the original response. Behaves like `reply` if nothing was sent yet. |
| `followUp(message)` | Send an extra message and get the created `Message` back. |
| `deleteReply()` | Delete the original response. |
| `showModal(modal)` | Open a modal. This must be the first response. |
| `isAcknowledged()` | Whether any response has been sent. |

Handlers run on virtual threads, so a slow operation can be written in a straight line:

```java
SlashCommand.of("report", "Build a report", e -> {
    e.defer();
    byte[] data = buildReport();
    e.reply(MessageBuilder.of("Done").file(FileUpload.of("report.csv", data)));
});
```

Files cannot be attached to the initial response, so if you reply with a file before answering, JDisc defers the interaction first and then edits in the message and attachment.

## Messages

`MessageBuilder` produces the payload used for interaction responses, ordinary channel messages and edits.

```java
MessageBuilder.of("Text")
        .embed(new EmbedBuilder().title("Title").description("Body").color(0x5865F2).now())
        .row(Component.primary("confirm", "Confirm"), Component.link("https://example.com", "Docs"))
        .ephemeral()
        .noMentions();
```

`ephemeral()` only has an effect on interaction responses. `replyTo(messageId)` is for channel messages. `safeMentions(false)` allows user pings only and does not ping the author of a replied-to message, and `noMentions()` suppresses every ping.

`EmbedBuilder` follows Discord's limits (6000 characters across an embed, 25 fields, 256 for a title, 4096 for a description) but does not validate them. Discord will reject an oversized embed with a `DiscordApiException`.

## Buttons

```java
bot.commands().register(
        SlashCommand.of("poll", "Start a poll", e -> e.reply(
                MessageBuilder.of("Pizza or pasta?").row(
                        Component.success("vote:pizza", "Pizza"),
                        Component.primary("vote:pasta", "Pasta")))));

bot.onButton("vote:*", e -> e.updateMessage("You voted " + e.customId().substring(5)));
```

Button styles are `primary`, `secondary`, `success` and `danger`, which all take a custom id and a label, and `link`, which takes a URL and has no custom id. Chain `.emoji(...)` and `.disabled()` as needed. An action row holds up to five buttons or one select menu.

Handlers are registered with `onButton`, `onSelectMenu` and `onModal`. The pattern is either an exact custom id or a prefix followed by `*`. Exact matches are tried first, then prefixes in the order they were registered. Putting data in the custom id after a prefix, like `vote:pizza` above, is the usual way to carry state. Custom ids are limited to 100 characters.

On a component event, `updateMessage(...)` edits the message that contains the component, and `deferUpdate()` acknowledges the click without changing anything. `reply` still works if you would rather send a new message.

If no handler matches a custom id, the event is dropped silently. You can also listen with `bot.on(ButtonEvent.class, ...)` to handle components yourself, for example ones created before the bot was last restarted.

## Select menus

```java
Component.stringSelect("color")
        .option("Red", "red")
        .option("Blue", "blue", "A calmer choice")
        .placeholder("Pick one")
        .range(1, 1)
```

Entity selects are created with `userSelect`, `roleSelect`, `mentionableSelect` and `channelSelect`. In the handler, `values()` returns what was chosen (option values for string selects, ids for the others), `firstValue()` is a shortcut for single selects, and `resolved()` gives access to the user, member, role and channel objects Discord sent along.

## Modals

```java
SlashCommand.of("feedback", "Send feedback", e -> e.showModal(
        Modal.create("feedback", "Feedback")
                .shortText("subject", "Subject")
                .paragraph("body", "Details")));

bot.onModal("feedback", e -> e.replyEphemeral(
        "Received: " + e.value("subject")));
```

Inputs are wrapped in label components, which is the layout Discord currently documents for modals. Use `add(label, description, component)` to place other components, such as selects, inside a label, and `text(markdown)` for plain text between inputs. `value(fieldId)` returns a text input's content, and `values(fieldId)` returns the selections of a multi-value component. Both search the whole submitted tree, so nesting does not matter.

## Components V2

Components V2 replaces content and embeds with a layout built from containers, sections and text blocks.

```java
bot.api().sendMessage(channelId, MessageBuilder.create().componentsV2(
        Component.container(0x5865F2,
                Component.text("# Release 0.1.0"),
                Component.separator(),
                Component.section(
                        Component.primary("notes", "Release notes"),
                        Component.text("Short summary of the changes")))));
```

`componentsV2` sets the message flag that Discord requires. A message that uses it cannot also carry `content` or embeds, and Discord does not let you switch the flag off again afterwards. The V2 builders available are `text`, `separator`, `container`, `section`, `thumbnail`, `mediaGallery` and `file`. A file component points at an attachment with `attachment://filename`.
