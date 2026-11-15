# REST

## The Api helper

`bot.api()` wraps the endpoints most bots need. Every method returns a `CompletableFuture`.

```java
bot.api().sendMessage(channelId, "Hello");
bot.api().sendDm(userId, MessageBuilder.of("Your report is ready"));
bot.api().addRole(guildId, userId, roleId, "Passed the quiz");
```

Inside a handler you are on a virtual thread, so blocking on the result is fine:

```java
Message sent = bot.api().sendMessage(channelId, "Working...").join();
bot.api().editMessage(channelId, sent.id(), MessageBuilder.of("Done"));
```

Outside a handler, for example in `main`, `join()` is also fine. Do not block inside a `thenApply` or `thenCompose` callback on a request that has not finished yet, because those callbacks run on the HTTP client's executor.

What is covered:

| Area | Methods |
| --- | --- |
| Users | `self`, `getUser`, `createDm`, `sendDm` |
| Messages | `sendMessage`, `editMessage`, `deleteMessage`, `getMessage`, `getMessages`, `bulkDelete`, `addReaction`, `removeOwnReaction`, `pin`, `unpin`, `typing` |
| Channels | `getChannel`, `deleteChannel` |
| Guilds | `getGuild`, `getChannels`, `getRoles`, `getMember`, `listMembers` |
| Moderation | `addRole`, `removeRole`, `kick`, `ban`, `unban`, `timeout` |
| Commands | `getGlobalCommands`, `deleteGlobalCommand` |

Methods that change something on a server take a `reason` argument. It is sent in the `X-Audit-Log-Reason` header and shows up in the audit log. It may be `null`.

Emoji arguments are either a unicode emoji or `name:id` for a custom one. They are URL-encoded for you.

## The raw client

For anything not listed above, build a `Route` and use `bot.rest()`:

```java
Route route = Route.get("/guilds/{}/preview", guildId);
JsonNode preview = bot.rest().request(route).join();

Guild guild = bot.rest().request(Route.get("/guilds/{}", guildId), null, Guild.class).join();

ObjectNode body = Json.object().put("name", "announcements").put("type", 0);
bot.rest().request(Route.post("/guilds/{}/channels", guildId), body, Channel.class);
```

`{}` placeholders are filled with the arguments in order. `Route.query(key, value)` appends a URL-encoded query parameter. The request methods are:

| Method | Result |
| --- | --- |
| `request(route)` | `JsonNode`, no body |
| `request(route, body)` | `JsonNode`, body serialized as JSON |
| `request(route, body, Type.class)` | the response converted to a model class |
| `requestList(route, body, Type.class)` | a list of model objects |
| `request(route, body, files, reason)` | multipart upload and/or audit log reason |

Bodies can be a Jackson node, a map or any object Jackson can serialize. Property names are written in snake_case, as the API expects. Empty responses (204) produce a JSON null node.

## Files

```java
MessageBuilder.of("Logs attached")
        .file(FileUpload.of(Path.of("app.log")))
        .file(FileUpload.text("notes.txt", "plain text"))
        .file(FileUpload.of("image.png", bytes).withDescription("Alt text"));
```

The multipart body is built in memory, so keep uploads within Discord's size limit for the server.

## Rate limiting

Every request goes through the rate limiter. You do not need to add delays or retries yourself.

- Requests are grouped by method, route template and major parameter (channel, guild or webhook). Requests in one group are sent one at a time, in order.
- The `X-RateLimit-*` headers update the group. When Discord reports a bucket id, groups that share it are merged, and the group that observed it keeps its state.
- When a group has no requests remaining, further requests wait until its reset time.
- A 429 response is retried after the delay Discord asks for, up to five attempts in total. A global 429 pauses every group.
- 5xx responses and network errors are retried up to three attempts in total, with a short growing delay.
- Independently of the headers, no more than 45 requests are started per second, which keeps a busy bot below the global limit of 50.

If the attempts run out, the future fails with the last error.

## Errors

A non-2xx response that is not retried completes the future exceptionally with a `DiscordApiException`.

```java
bot.api().kick(guildId, userId, "spam").exceptionally(error -> {
    if (error.getCause() instanceof DiscordApiException api && api.code() == 50013) {
        log.warn("Missing permissions to kick {}", userId);
    }
    return null;
});
```

`status()` is the HTTP status, `code()` is Discord's JSON error code (50013 for missing permissions, 10003 for an unknown channel, and so on), and `errors()` holds the field-level details when Discord provides them. When you call `join()`, the exception is wrapped in a `CompletionException`.

## Pointing at another base URL

The base URL defaults to `https://discord.com/api/v10`. Set the system property `jdisc.api.base` before the bot is built to send requests somewhere else, such as a local test server or an HTTP proxy that implements the same API:

```
java -Djdisc.api.base=http://localhost:8080/api/v10 -jar bot.jar
```

The test suite relies on this to run interactions against a local server.
