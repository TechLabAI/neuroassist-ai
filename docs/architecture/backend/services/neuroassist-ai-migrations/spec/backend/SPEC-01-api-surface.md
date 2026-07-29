# SPEC-01 — API Surface

> ADR refs: **ADR-002** (centralized migrations), **ADR-005** (tenant
> provisioning). Contracts: api-contract.md (full).

## Objective

Define MIG's internal-only API surface — no public Swagger, no GWY route —
covering the job invocation contract and the small HTTP face ACS/operators
use.

## Scope

- **In:** job `--mode` dispatch, internal HTTP endpoints (provision, status,
  run), auth, error shape.
- **Out:** business REST resources (MIG owns none), tenant deprovisioning
  endpoint (not automated — G1).

## Technical decisions

- Internal HTTP API is service-to-service only (`migrations:internal` /
  `migrations:admin` OAuth2 scopes), never exposed via GWY.
- The job path (`--mode=...`) is the deploy-time primary path; the HTTP path
  exists for ACS's synchronous onboarding call and for operator status/info.
- Errors are RFC 7807 `application/problem+json` with a stable `code` field.

## Implementation

- `api/` package: thin `MigrationsJobRunner` (CLI `--mode` dispatch) +
  `InternalMigrationsController` (`PUT /internal/migrations/tenants/{id}`,
  `GET /internal/migrations/status`, `POST /internal/migrations/run`) +
  `ProblemDetailMapper`.
- Both paths call into the same `application` use cases (SPEC-02/SPEC-03) —
  no duplicated orchestration logic.

## Edge cases

- Missing/invalid `--mode` → exit non-zero + usage listing valid modes.
- Provisioning call for an unknown `tenantId` (not in the registry) → 400
  `invalid-tenant-id`.
- Concurrent `POST /internal/migrations/run` while a run is active → 409
  `run-in-progress`.

## Acceptance criteria (EARS)

`THE SYSTEM SHALL expose the migration API on an internal surface only and SHALL NOT publish a public Swagger/OpenAPI document.`
`THE SYSTEM SHALL NOT route any migration endpoint through the API gateway (GWY) or under the public /api/** edge.`
`THE SYSTEM SHALL require the migrations:internal or migrations:admin OAuth2 scope on every internal endpoint.`
`IF a run is already in progress, THEN THE SYSTEM SHALL respond 409 with code run-in-progress.`
`THE SYSTEM SHALL return all API errors as application/problem+json (RFC 7807) with a stable code field.`
