# Wizard v1 Platform

Wizard v1 generates Android starter projects from a manifest-driven catalog. Current output targets Android-only, multi-module Gradle projects with curated UI, architecture, DI, library, quality, and CI packs.

## Modules

- `contracts:core` - public API contracts, lockfile, selection model
- `contracts:manifest` - plugin pack manifest schema
- `engine:*` - catalog, configuration, generator, resolver, security
- `plugins:*` - in-repo generator content packs
- `server:api` - Ktor API `/api/v1/*`
- `web:app` - 6-step Kotlin/JS wizard UI
- `build-logic` - repo convention plugins

## Generator coverage

- Android project skeleton: `app`, `core:*`, `feature:<name>:presentation/domain/data`
- Architectures: `MVVM`, `MVI`, `MVP`, plus custom component templates
- UI: `Compose`, `XML Views`
- DI: `Hilt`, `Koin`, `Dagger 2`
- Libraries: `Retrofit/OkHttp`, `Room`, `Coil`, `Timber`
- Quality: `detekt`, `ktlint`
- CI/CD: `GitHub Actions`, `GitLab CI`
- Export: JSON preview metadata, ZIP artifact, binary ZIP download

## Build (Java 21)

```bash
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew build --no-daemon
```

## Governance

- dependency locking is enabled
- compatibility gates run via `./gradlew compatibilityCheck`
- lockfile policy runs via `./gradlew lockfileCheck`

## API

- `GET /api/v1/catalog`
- `POST /api/v1/resolve`
- `POST /api/v1/preview`
- `POST /api/v1/export`
- `POST /api/v1/export/download`
- `GET /api/v1/health`
