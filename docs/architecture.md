# Wizard v1 Architecture

## Principles

- Manifest-driven extensibility for dependencies, architectures, and UI frameworks.
- Deterministic resolution and generation.
- Strict compatibility and version governance.
- Resource-backed classpath templates for reusable project skeletons.

## Layers

1. Contracts
- `contracts:core` and `contracts:manifest` define API and manifest schemas.

2. Engine
- `engine:catalog` merges local+remote packs (local precedence).
- `engine:resolver` resolves dependencies/capabilities/conflicts and emits lockfiles.
- `engine:generator` applies ordered patch pipeline and classpath template expansion.
- `engine:security` verifies SHA-256 checksums.

3. Delivery
- `server:api` exposes `/api/v1`.
- `web:app` renders wizard steps from catalog metadata.

4. Content
- `plugins:*` modules provide versioned plugin packs.
- packs are split by concern: base/core, android target, UI, architecture, DI, libraries, quality, CI.

## Data Flow

1. Web loads `/api/v1/catalog`.
2. User selections are sent to `/api/v1/resolve`.
3. Resolver returns ordered options, issues, and `WizardLockfile`.
4. `/api/v1/preview` and `/api/v1/export` require lockfile in strict mode.
5. Generator expands resource templates, applies option patches, and synthesizes per-feature modules.
6. `/api/v1/export/download` streams the resolved ZIP artifact directly.

## Generated project shape

- fixed base modules: `app`, `core:common`, `core:ui`, `core:designsystem`, `core:network`, `core:database`, `core:testing`
- repeated feature modules per configured feature name:
  - `feature:<name>:presentation`
  - `feature:<name>:domain`
  - `feature:<name>:data`
- architecture preset generation and custom component generation are layered on top of the base feature module templates
