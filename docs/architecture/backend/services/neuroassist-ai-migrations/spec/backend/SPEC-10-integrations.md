# SPEC-10 — Integrations

> Contracts: integration-contracts.md (full), hub-integration.md §5.

## Objective

Enumerate MIG's complete external-integration surface: PostgreSQL, the
deploy pipeline, and ACS — and record that nothing else exists (no PSP, no
AI, no broker).

## Scope

- **In:** PostgreSQL contract, deploy-pipeline gate, ACS onboarding call.
- **Out:** any SaaS/PSP/AI/broker integration (none exists).

## Technical decisions

- PostgreSQL is the **only** external system with a runtime dependency
  (privileged DDL role, single instance — A2).
- The deploy pipeline is the primary **driver**, not a callee: it invokes
  MIG as a run-to-completion job and gates rollout on the exit code.
- ACS is the only **caller** of MIG's internal HTTP face (tenant
  provisioning, ADR-005).

## Implementation

- `infrastructure`: `PostgresAdminAdapter` (privileged JDBC), `FlywayAdapter`.
- No Feign client toward any external system beyond PostgreSQL's JDBC
  driver (there is no HTTP integration for MIG to call outward — it is
  always the callee).

## Edge cases

- PostgreSQL unreachable at pre-flight → exit 3, no DDL (SPEC-02/SPEC-05).
- Deploy pipeline invokes `migrate-all` twice in quick succession (retry
  logic) → second invocation is safely idempotent (SPEC-05).

## Acceptance criteria (EARS)

`THE SYSTEM SHALL integrate with PostgreSQL and the deploy pipeline only, plus ACS as the sole internal-API caller.`
`THE SYSTEM SHALL declare no PSP, AI, or message-broker integration.`
