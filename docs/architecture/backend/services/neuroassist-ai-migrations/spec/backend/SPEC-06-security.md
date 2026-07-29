# SPEC-06 — Security

> ADR refs: **ADR-002** (least-privilege DDL role). Contracts: api-contract
> §1.4, hub-integration §1/§4.

## Objective

Confine DDL privilege to MIG alone, keep its API internal-only, and support
LGPD auditability of every schema change (doc 01 Architectural Principles
§5, R3).

## Scope

- **In:** privileged role isolation, OAuth2 scopes for the internal API,
  no-public-exposure guardrail, audit trail via Flyway history.
- **Out:** row-level authorization inside tenant tables (owned by ACS/PAC
  once specced); encryption-at-rest (infra-level, out of MIG's scope).

## Technical decisions

- **Least privilege:** MIG's PostgreSQL role has `CREATE SCHEMA` + DDL
  privileges; business services get DML-only roles against already-migrated
  schemas — never the same credential (R3).
- **Internal-only API:** `migrations:internal` scope for ACS's provisioning
  call, `migrations:admin` scope for the manual `run` trigger; no end-user
  token accepted; no GWY route.
- **No destructive DDL without review:** drop/rename operations are never
  auto-generated; they require an explicit, reviewed migration (R1) — and
  tenant schema drop is out of MVP scope entirely (G1).
- **Auditability:** the per-schema Flyway history is the immutable ledger a
  security/LGPD audit reads (doc 05 §Data Lifecycle).

## Implementation

- Two distinct PostgreSQL roles at the infra level: `neuroassist_migrator`
  (MIG, privileged) and `neuroassist_app` (business services, DML-only per
  schema they're granted).
- `SecurityConfig` (infrastructure) validates the OAuth2 scope per endpoint;
  `ProblemDetailMapper` returns 403 on scope mismatch.

## Edge cases

- A request reaches the internal API without any scope → 401.
- A request carries `migrations:internal` but calls `POST
  /internal/migrations/run` (admin-only) → 403.
- A migration file contains a `DROP`/`RENAME` statement → flagged for manual
  review in the PR process (convention, not a runtime check in MVP).

## Acceptance criteria (EARS)

`THE SYSTEM SHALL use a privileged DDL role distinct from every business service's DML-only role.`
`THE SYSTEM SHALL require the migrations:internal or migrations:admin OAuth2 scope on every internal endpoint and SHALL reject requests with neither.`
`THE SYSTEM SHALL reject migrations:internal-scoped requests to admin-only endpoints with 403.`
`THE SYSTEM SHALL NOT expose any migration endpoint through the API gateway or to an end-user token.`
`THE SYSTEM SHALL retain the Flyway history of every schema as the auditable record of structural changes.`
