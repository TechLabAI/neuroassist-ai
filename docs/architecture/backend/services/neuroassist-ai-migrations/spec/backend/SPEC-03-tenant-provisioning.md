# SPEC-03 — Tenant Schema Provisioning

> ADR refs: **ADR-005** (tenant schema provisioning strategy). Contracts:
> api-contract §1.1/§2, data-model §2.3, hub-integration §5.
>
> Resolves G4 (doc 06): the exact contract of the synchronous provisioning
> trigger from ACS.

## Objective

Provision a new empresa's schema (`t_<tenantId>`) synchronously,
idempotently and deterministically when ACS invokes it at onboarding, per
ADR-005.

## Scope

- **In:** synchronous provisioning call, idempotent re-provisioning,
  global→tenant ordering, tenant-template application.
- **Out:** deprovisioning/offboarding (not automated — G1, manual reviewed
  operation), per-tipo template divergence (A3 — template is identical for
  familia/clinica in MVP).

## Technical decisions

- **Synchronous:** ACS calls `PUT /internal/migrations/tenants/{tenantId}`
  (or the `provision-tenant` job mode) and blocks until MIG returns
  success/failure — no broker, no async callback (messaging disabled).
- **Idempotent:** re-invoking for a tenant already at head is a no-op success
  (`status: "provisioned"`, empty `appliedVersions`).
- **Deterministic:** the identical tenant-template migration set is applied
  regardless of `tenant.tipo`.
- **Ordering:** MIG verifies (or brings) the global schema to head before
  provisioning any tenant schema in the same call (A5).
- **Fail-fast:** a checksum mismatch or DDL error aborts the provisioning
  call; the schema is left at its last successful version; no auto-repair.

## Implementation

- `application`: `ProvisionTenantSchema` use case — validates `tenantId`
  exists in the `tenant` registry, creates `t_<tenantId>` if absent,
  applies the tenant-template set to head, returns the applied versions.
- Advisory lock on the target schema for the duration of the call (shared
  with SPEC-05's concurrency guard).

## Edge cases

- `tenantId` not present in the `tenant` registry → 400 `invalid-tenant-id`,
  no DDL executed.
- Concurrent provisioning calls for the same `tenantId` → second call waits
  on the advisory lock, then observes the already-provisioned state (no-op).
- Global schema behind head at call time → MIG migrates global first, then
  proceeds with the tenant (never provisions against a stale global).

## Acceptance criteria (EARS)

`WHEN ACS invokes PUT /internal/migrations/tenants/{tenantId} for a tenantId present in the tenant registry, THE SYSTEM SHALL create the schema if absent and migrate it to head before responding.`
`WHEN the same tenantId is provisioned again after reaching head, THE SYSTEM SHALL respond success with no DDL applied (idempotent no-op).`
`IF tenantId is not present in the tenant registry, THEN THE SYSTEM SHALL respond 400 with code invalid-tenant-id and SHALL apply no DDL.`
`THE SYSTEM SHALL apply the identical tenant-template set regardless of the tenant's tipo (familia|clinica).`
`THE SYSTEM SHALL ensure the global schema is at head before provisioning a tenant schema within the same call.`
