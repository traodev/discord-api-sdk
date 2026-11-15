# Contributing

Bug reports, fixes and documentation improvements are welcome.

## Building

You need JDK 21. The Gradle wrapper is included, so nothing else has to be installed.

```
./gradlew build
```

This compiles the library and runs the tests. There is no setup beyond that, and no test needs a Discord token or network access.

## Making a change

1. Open an issue first for anything larger than a small fix, so the approach can be agreed before you spend time on it.
2. Add or update tests next to the change. Gateway behaviour is tested with the fake server in `GatewayTest`, REST behaviour with `RateLimiterTest` and `InteractionFlowTest`. Extend those rather than adding mocks of your own.
3. Update the relevant page in `docs/` when behaviour visible to users changes, and add a line to `CHANGELOG.md` under "Unreleased".
4. Keep pull requests focused. A refactor and a bug fix belong in separate pull requests.

## Style

- Four spaces, no tabs, no wildcard imports.
- Public types are small and immutable where possible. Models are records.
- The code carries no comments. Prefer a clearer name or a smaller method to explaining something in a comment, and put longer explanations of behaviour in `docs/`.
- New public API should be reachable from `Bot`, `Api` or an event class, and should be usable without reading the source.

## Adding an event

1. Add a record implementing `Event` in `fr.traodev.event`.
2. Register a decoder for it in `EventDispatcher`.
3. Add it to the table in `docs/events.md`, with the intent it needs.
4. Add a test in `ModelsAndDispatchTest` or alongside the feature.

## Adding a REST helper

Add a method to `Api` that builds a `Route` and returns a typed future. Use the audit log reason parameter for anything that changes server state. Endpoints that are rarely used do not need a helper, since `bot.rest()` already reaches them.

## Publishing

The group id and base package are `fr.traodev`. Publishing to Maven Central requires a verified namespace, which means proving control of the matching domain, so the namespace has to be verified before the first release. The build already produces the sources and Javadoc jars and a Maven publication with the POM metadata. Signing and the upload to a repository are not configured yet and need to be added with your own credentials.

## Reporting security problems

Do not open a public issue for a vulnerability. Contact the maintainers privately through the repository's security advisory feature.
