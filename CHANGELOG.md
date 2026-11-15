# Changelog

All notable changes are recorded here. The format follows Keep a Changelog and the project follows semantic versioning.

## Unreleased

### Added

- Gateway client for API v10: heartbeat with dead-connection detection, session resume, zlib-stream compression, sharding with `max_concurrency` aware IDENTIFY, close-code handling.
- REST client on the JDK HTTP client with per-bucket rate limiting, a global limiter, and retries for 429, 5xx and network errors.
- Typed events with lazy decoding, raw event listeners, and a virtual-thread executor for handlers.
- Slash commands with options, subcommands, groups, autocomplete, permissions, localization and automatic synchronization; user and message context menu commands.
- Interaction responses with automatic reply, defer and follow-up handling; buttons, select menus and modals with prefix routing; Components V2 builders.
- `Api` helper for common endpoints and a raw route API for the rest.
- Test suite built on an in-process gateway and REST server.
- Gradle build (Kotlin DSL) with wrapper, sources and Javadoc jars, and a Maven publication.
