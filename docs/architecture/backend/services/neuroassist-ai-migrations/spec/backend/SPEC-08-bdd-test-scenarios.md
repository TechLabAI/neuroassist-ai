# SPEC-08 — BDD Test Scenarios

> Gherkin lives in `tests/cucumber/*.feature` (pure Gherkin, not this file).
> This spec is the prose index + fixture/step notes.

## Objective

Index the BDD coverage for MIG's three testable surfaces: orchestration
(global + tenant fan-out), tenant provisioning, and idempotency/concurrency
+ checksum drift.

## Scope

- **In:** scenario index, shared fixtures/background, reusable step
  vocabulary.
- **Out:** the Gherkin itself (see `tests/cucumber/`).

## Feature index

| Feature file | Covers | Key scenarios |
|---|---|---|
| `migration-orchestration.feature` | SPEC-02 | migrate-all ordering (global before tenant), fan-out over the tenant registry, partial tenant failure keeps exit 2 but continues fan-out |
| `tenant-provisioning.feature` | SPEC-03 | provision new tenant, idempotent re-provision, unknown tenantId rejected before DDL |
| `idempotency-and-drift.feature` | SPEC-05 | re-run no-op, advisory lock serializes concurrent runs, checksum drift aborts without auto-repair |

## Shared fixtures / background

- A `tenant` registry seeded with 1+ rows (`familia`/`clinica` mix) before
  fan-out scenarios.
- A Testcontainers PostgreSQL instance per test run (isolated schema
  namespace).
- A fixed "current version" fixture (`V0002`) representing the tenant
  template head at spec time.

## Reusable step vocabulary

- `Given the global schema neuroassist_global exists`
- `Given the tenant registry contains tenant "<tenantId>" of tipo "<tipo>"`
- `When MIG runs with mode "<mode>"` / `... and tenant "<tenantId>"`
- `Then the run exits with code <code>`
- `Then schema "<schema>" is at version "<version>"`
- `Then the run report marks schema "<schema>" as "<status>"`

## Acceptance criteria (EARS)

`THE SYSTEM SHALL have at least one BDD scenario per SPEC-02/SPEC-03/SPEC-05 acceptance criterion.`
`THE SYSTEM SHALL run every *.feature file against a Testcontainers PostgreSQL instance, never against a shared/persistent database.`
