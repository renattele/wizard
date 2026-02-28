# API v1 Contracts

Base path: `/api/v1`

## Endpoints

- `GET /health`
- `GET /catalog`
- `POST /resolve`
- `POST /preview`
- `POST /export`
- `GET /openapi`

## Core Models

- `ResolveRequestV1` / `ResolveResponseV1`
- `PreviewRequestV1` / `PreviewResponseV1`
- `ExportRequestV1` / `ExportResponseV1`
- `CompatibilityReportV1`
- `WizardLockfile`

## Strict Mode

When `strictMode = true`:

- `/preview` and `/export` require lockfile.
- stale lockfile hash causes request rejection.
