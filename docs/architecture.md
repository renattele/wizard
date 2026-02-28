# Wizard v1 Architecture

## Principles

- Manifest-driven extensibility for dependencies, architectures, and UI frameworks.
- Deterministic resolution and generation.
- Strict compatibility and version governance.

## Layers

1. Contracts
- `contracts:core` and `contracts:manifest` define API and manifest schemas.

2. Engine
- `engine:catalog` merges local+remote packs (local precedence).
- `engine:resolver` resolves dependencies/capabilities/conflicts and emits lockfiles.
- `engine:generator` applies ordered patch pipeline.
- `engine:security` verifies SHA-256 checksums.

3. Delivery
- `server:api` exposes `/api/v1`.
- `web:app` renders wizard steps from catalog metadata.

4. Content
- `plugins:*` modules provide versioned plugin packs.

## Data Flow

1. Web loads `/api/v1/catalog`.
2. User selections are sent to `/api/v1/resolve`.
3. Resolver returns ordered options, issues, and `WizardLockfile`.
4. `/api/v1/preview` and `/api/v1/export` require lockfile in strict mode.
5. Generator applies option patches in deterministic order.
