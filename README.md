# Wizard v1 Platform

Wizard v1 is a manifest-driven project generation platform with strict dependency/version governance.

## Modules

- `contracts:core` - API v1 contracts and lockfile model
- `contracts:manifest` - plugin pack manifests and compatibility contracts
- `engine:*` - catalog, resolver, generator, security engines
- `plugins:*` - in-repo plugin packs
- `server:api` - Ktor API `/api/v1/*`
- `web:app` - Kotlin/JS metadata-driven UI
- `build-logic` - convention plugins

## Build (Java 21)

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew build --no-daemon
```

## Governance

- dependency locking is enabled
- compatibility gates run via `./gradlew compatibilityCheck`
- lockfile policy runs via `./gradlew lockfileCheck`

## API

- `/api/v1/catalog`
- `/api/v1/resolve`
- `/api/v1/preview`
- `/api/v1/export`
- `/api/v1/health`
