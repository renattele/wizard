# API v1 Contracts

Base path: `/api/v1`

## Endpoints

- `GET /health`
- `GET /catalog`
- `POST /resolve`
- `POST /preview`
- `POST /export`
- `POST /export/download`
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

## Selection requirements

`WizardSelectionV1.projectConfig` is expected to include:

- `projectName`
- `packageId`
- `featureNames`
- `modulePreset = "android-clean"`
- `releaseTarget = "git-release-assets"`
- `releaseArtifactTypes = ["apk", "aab"]`

## Download endpoint

`POST /export/download` accepts the same body as `/export` and returns the ZIP bytes directly with attachment headers.
