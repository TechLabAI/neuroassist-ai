# MIG — API / Operational Contract

> MIG is an **infrastructure** service. Its API is **internal-only** — it is not
> a business REST surface, it is **not routed by GWY**, and it exposes **no
> public Swagger/OpenAPI**. It has two faces: (1) a **run-to-completion job**
> (the deploy-time primary path, with `--mode` + exit codes), and (2) a small
> **internal HTTP API** used by ACS (tenant provisioning trigger, ADR-005) and
> by operators (status/info).
>
> Authoritative for SPEC-01 (API surface), SPEC-03 (tenant provisioning) and
> SPEC-07 (observability).

## 1. Internal HTTP API (service-to-service, not public, no GWY route)

These endpoints live off the public `/api/**` edge. They are reachable only
service-to-service (ACS) or by operators, and **never** through the API
gateway. No Swagger/OpenAPI document is published externally.

| Path | Verb | Purpose | Auth |
|---|---|---|---|
| `PUT /internal/migrations/tenants/{tenantId}` | trigger | Synchronous tenant provisioning (create `t_<tenantId>` if absent + migrate to head, ADR-005) | internal (service-to-service) |
| `GET /internal/migrations/status` | read | Current applied version of the global schema + tenant schemas (sample by default, all on `?scope=all`) | internal |
| `POST /internal/migrations/run` | trigger | Manual migration run (`migrate-all` \| `migrate-global`); admin-only | internal (admin) |
| `GET /actuator/health/readiness` | health | Readiness of the HTTP face | internal |

### 1.1 `PUT /internal/migrations/tenants/{tenantId}` (ADR-005)

Called synchronously by ACS at empresa onboarding. Idempotent: re-invoking a
tenant already at head is a no-op success.

```json
// request
{}
// response 200 (created or already-current)
{
  "tenantId": "b3f1...-uuid",
  "schema": "t_b3f1uuid",
  "status": "provisioned",
  "appliedVersions": ["V0002", "V0003"],
  "currentVersion": "V0003",
  "correlationId": "..."
}
```

### 1.2 `GET /internal/migrations/status`

```json
{
  "global": { "schema": "neuroassist_global", "currentVersion": "V0001", "pending": [] },
  "tenants": [
    { "schema": "t_b3f1uuid", "currentVersion": "V0003", "pending": [] },
    { "schema": "t_9a2cuuid", "currentVersion": "V0002", "pending": ["V0003"] }
  ],
  "sampledAt": "ISO-8601"
}
```

### 1.3 `POST /internal/migrations/run` (admin-only)

```json
// request
{ "mode": "migrate-all" }
// response: 202 Accepted + the JSON run report (§3) once the run completes
```

### 1.4 Headers and errors (internal API)

- **Headers:** `X-Correlation-Id` (propagated/generated, echoed on every
  response and recorded in the activity trail — D-423), `X-Internal-Auth`
  (required on `POST /internal/migrations/run`).
- **Auth:** OAuth2 client-credentials with the `migrations:internal` scope
  (ACS) or `migrations:admin` scope (operators) — service-to-service /
  operator only; **never** an end-user token, **never** via GWY.
- **Errors:** RFC 7807 `application/problem+json` with stable `code` values:
  `invalid-tenant-id`, `internal-auth-required`, `schema-not-found`,
  `already-current`, `run-in-progress`, `migration-failed`,
  `preflight-failed`, `checksum-drift`.

### Invariants (EARS)

`THE SYSTEM SHALL expose the migration API on an internal surface only and SHALL NOT publish a public Swagger/OpenAPI document.`
`THE SYSTEM SHALL NOT route any migration endpoint through the API gateway (GWY) or under the public /api/** edge.`
`THE SYSTEM SHALL require the migrations:internal or migrations:admin OAuth2 scope on every internal endpoint.`
`WHEN GET /internal/migrations/status is called, THE SYSTEM SHALL return the current applied version of the global schema and of the tenant schemas (sample by default, all on ?scope=all).`
`IF a run is already in progress, THEN THE SYSTEM SHALL respond 409 with code run-in-progress and SHALL NOT start a second concurrent run against the same target.`

## 2. Job invocation contract (deploy-time primary path)

MIG's deploy-time path is a job invoked with exactly one **mode**. Each mode
is deterministic and fail-fast on drift (ADR-002/ADR-005).

| Mode | Invocation | Targets | Effect |
|---|---|---|---|
| `migrate-all` | `--mode=migrate-all` (deploy default) | `neuroassist_global` + every existing tenant schema | Apply pending migrations to each target in deterministic order |
| `migrate-global` | `--mode=migrate-global` | `neuroassist_global` only | Apply the global set only |
| `provision-tenant` | `--mode=provision-tenant --tenant=<tenantId>` | new `t_<tenantId>` | Create schema if absent + apply the tenant template to head |
| `info` | `--mode=info [--tenant=<tenantId>]` | selected schema(s) | Read-only: print applied/pending per schema; no DDL |

Common flags: `--dry-run` (plan only, write nothing).

### Invariants (EARS)

`THE SYSTEM SHALL require exactly one --mode per invocation.`
`WHEN invoked without --mode, THE SYSTEM SHALL exit non-zero and print the list of valid modes.`
`WHERE --mode is provision-tenant, THE SYSTEM SHALL require a --tenant value that is a valid UUID matching an entry in the global tenant registry.`
`WHERE --dry-run is set, THE SYSTEM SHALL plan and report the intended actions and SHALL execute no DDL.`

## 3. Exit codes + run report (pipeline gate)

The deploy pipeline gates the business-service rollout on MIG's exit code.

| Code | Meaning |
|---|---|
| `0` | All targeted schemas converged to the expected version; nothing pending |
| `1` | User/invocation error (bad mode, missing/invalid `--tenant`) |
| `2` | Migration failure on one or more schemas (fleet at mixed versions — do NOT roll out) |
| `3` | Pre-flight failure (cannot connect to PostgreSQL, privileged role missing, tenant registry unreadable) |

Every run emits a JSON report (stdout):

```json
{
  "runId": "uuid",
  "mode": "migrate-all",
  "startedAt": "ISO-8601",
  "finishedAt": "ISO-8601",
  "schemas": [
    { "schema": "neuroassist_global", "scope": "global", "applied": ["V0001"], "pending": [], "status": "ok" },
    { "schema": "t_b3f1uuid", "scope": "empresa", "applied": ["V0002","V0003"], "pending": [], "status": "ok" },
    { "schema": "t_9a2cuuid", "scope": "empresa", "applied": ["V0002"], "pending": ["V0003"], "status": "failed", "error": "checksum mismatch" }
  ],
  "summary": { "total": 3, "ok": 2, "failed": 1 },
  "exitCode": 2
}
```

### Invariants (EARS)

`WHEN every targeted schema is at the expected version after the run, THE SYSTEM SHALL exit 0.`
`IF any targeted schema fails its migration, THEN THE SYSTEM SHALL exit 2 and SHALL report which schemas failed.`
`IF MIG cannot reach PostgreSQL, cannot assume the privileged role, or cannot read the tenant registry, THEN THE SYSTEM SHALL exit 3 before applying any DDL.`
`THE SYSTEM SHALL emit one run report per invocation containing a per-schema applied/pending/failed entry, with report.exitCode equal to the process exit code.`

## 4. What MIG does NOT expose

- No business REST resources (it owns no domain).
- No public/edge surface; no GWY route; no external Swagger/OpenAPI.
- No tenant-deprovisioning endpoint (manual, reviewed operation — G1; out of MVP scope).
- No down-migration / rollback endpoint.
