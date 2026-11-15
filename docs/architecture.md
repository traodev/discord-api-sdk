# Architecture

This page describes how the pieces fit together, for people who want to change JDisc or understand its behaviour under failure.

## Overview

```
Bot
 |-- RestClient ---- RateLimiter ---- java.net.http.HttpClient (HTTP/2)
 |-- ShardManager
 |     `-- GatewayShard (one per shard) ---- java.net.http.WebSocket
 |-- EventDispatcher ---- EventBus ---- your listeners
 `-- CommandRegistry, component routers
```

`Bot` owns a virtual-thread executor and a single platform thread used as a scheduler for timers (heartbeats, rate-limit waits, reconnect delays). Nothing blocks the scheduler thread: timers only submit work.

## Gateway

Each `GatewayShard` runs this lifecycle:

1. Connect to `wss://...?v=10&encoding=json&compress=zlib-stream`.
2. On HELLO, start the heartbeat with a random initial delay as Discord specifies, then either send RESUME (if a session id is held and the previous close allows it) or wait for an IDENTIFY slot and send IDENTIFY.
3. On READY, store the session id and resume URL, reset the reconnect counter and notify the bot.
4. Process dispatches. Each one updates the sequence number, then goes to the bot.

Reconnection follows what Discord documents:

- If a heartbeat is due and the previous one was never acknowledged, the connection is treated as dead, aborted without a close frame, and resumed.
- Opcode 7 (RECONNECT) aborts the connection and resumes.
- Opcode 9 (INVALID_SESSION) waits a randomized delay of at least one second, then resumes if Discord said that is possible, or otherwise forgets the session and identifies again.
- Close codes 4007, 4009, 1000 and 1001 invalidate the session. Other close codes resume.
- Close codes 4004, 4010, 4011, 4012, 4013 and 4014 are fatal. The shard does not reconnect, a `ShardDisconnectEvent` with `willReconnect == false` is raised, and the bot fails with an exception that `awaitShutdown()` rethrows.
- Reconnect attempts back off exponentially, starting immediately for the first attempt, then 2 seconds, doubling up to 30 seconds, with up to 500 ms of random jitter added.

Every callback from a socket is tagged with a generation number. When a connection is replaced, callbacks from the old socket see a different generation and are ignored.

Outgoing commands (presence updates and the like) are limited to 110 per minute, below Discord's limit of 120, and sends on one socket are chained so frames are never written concurrently. Heartbeats, IDENTIFY and RESUME bypass that limit.

### Identify concurrency

`ShardManager` reads `max_concurrency` from `/gateway/bot`. Shards whose id has the same remainder modulo `max_concurrency` share an IDENTIFY slot and start at least 5.1 seconds apart, while shards in different slots start together. Startup fails early if Discord reports fewer remaining session starts than shards.

### Compression

The `zlib-stream` transport keeps one inflate context for the life of the connection. `ZlibStreamDecoder` buffers frames until the buffer ends with the `00 00 FF FF` sync marker and then inflates the whole message. A new decoder is created for every connection because the compression context is per connection.

## REST

`RestClient` builds the request and hands it to the `RateLimiter`, which owns the queues. See [REST](rest.md#rate-limiting) for the observable behaviour. Internally, each bucket is a small state machine holding a queue, a remaining count and a reset time. A bucket sends one request, waits for the response, updates itself from the headers, and then moves to the next request, which keeps header updates race-free at the cost of serializing requests that share a bucket.

## Events

`EventDispatcher` holds a table from gateway event name to a decoder function and the event class it produces. Before decoding, it asks the `EventBus` whether anyone listens to that class, or to one of its superclasses. Interaction payloads are always decoded, because the command and component routers are listeners themselves and need them.

`EventBus.publish` walks the class hierarchy of the event and submits one task per listener to the virtual-thread executor.

## Models

Models are records. Jackson is configured with a snake_case naming strategy, unknown properties ignored and nulls omitted on output. Snowflake ids are `long` fields, which Jackson fills from the strings Discord sends. Absent ids read as 0. Collections that are absent are replaced by empty collections in each record's constructor.

Records combined with a naming strategy do not deserialize correctly on Jackson 2.14 and earlier, which fails with errors about setting final fields. The tests were run with 2.15.2, and `build.gradle.kts` pins 2.17.2. If your application forces an older Jackson version, expect those errors.

Outgoing payloads are not records. They are built as Jackson trees by `MessageBuilder`, `EmbedBuilder`, `Component`, `Modal`, `SlashCommand` and `Option`, which keeps optional fields easy to omit.

## Tests

The test classes in `src/test` cover:

- `GatewayTest`: a minimal RFC 6455 server running in the test process plays Discord's side of the connection, with zlib-stream frames, and checks HELLO, IDENTIFY contents, READY, a dispatched message, a heartbeat carrying the last sequence number, and RECONNECT followed by RESUME on a new connection.
- `InteractionFlowTest`: a local HTTP server records the REST calls produced by slash commands, defer and edit, follow-ups, button routing and the modal round trip.
- `RateLimiterTest`: a fake transport checks 429 retry, error mapping and waiting for an exhausted bucket.
- `ZlibStreamDecoderTest`, `ModelsAndDispatchTest` and `CommandsTest`: decoding with a shared compression context and split frames, record parsing, lazy dispatch, command JSON, routers and builders.

None of these talk to Discord. Behaviour against the real service, especially around rate-limit headers and gateway edge cases, is exercised only by running a bot.
