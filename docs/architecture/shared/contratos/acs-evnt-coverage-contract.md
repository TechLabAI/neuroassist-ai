# ACS → EVNT Coverage Contract

> Document version: **1.0.0** (Frozen 2026-08-16 — INT-FE-ACS-005)
>
> Owner: ACS module (FE + BE)
> Consumer: EVNT module
> Breaking change policy: any change to the types or eligibility rule below requires
> coordination with the EVNT team and a version bump of this document.

---

## Purpose

This document defines the contract by which the ACS module signals patient coverage
eligibility to the EVNT (Eventos e Alarmes) module. EVNT must know whether a patient
is eligible for alarm routing — i.e., whether there is at least one active caregiver
willing and able to receive the alert.

---

## Eligibility Rule

A patient is **coverage-eligible** (eligible for alarm routing by EVNT) when:

```
CoverageVm.isCovered === true
```

where `isCovered` is derived as:

```
isCovered = coveredBy.length > 0
```

and `coveredBy` is the list of **active** caregiver IDs (status `active`) that have
an **active** link (ACS link entity with `active = true`) to this patient.

**Invariant (ADR-005):** The ACS module enforces that no caregiver deactivation or
link revocation is permitted when it would leave a patient with `isCovered = false`.
This enforcement happens:
1. Pre-flight in the FE (facade checks coverage map before calling the adapter).
2. At the BE (`acs-service`) which returns HTTP 409 for last-covering-block attempts.

EVNT may therefore trust that, **in normal operation**, any patient record it receives
will have at least one active caregiver. However, EVNT must still handle `isCovered = false`
defensively (e.g., during system initialization, data migration, or if the invariant
was violated by a direct DB write outside the application).

---

## Exported Type (FE)

The `@neuroassist/acs` barrel exports the `CoverageVm` type for consumption by
`scope:web` shell compositions. EVNT FE components must not import from `@neuroassist/acs`
directly (Nx boundary: `scope:evnt` must not depend on `scope:acs`). Instead, the
shell page is responsible for reading coverage state and passing it as inputs.

```typescript
// Exported from @neuroassist/acs (index.ts)
export type CoverageVm = {
  /** Opaque patient ID (matches PAC module patient record). */
  readonly pacienteId: string;
  /** Opaque IDs of active caregivers currently covering this patient. */
  readonly coveredBy: readonly string[];
  /** True when coveredBy.length > 0 — the canonical eligibility signal for EVNT. */
  readonly isCovered: boolean;
};
```

---

## BE Events Emitted by ACS (acs-service → ARTEMIS)

The BE `acs-service` emits the following events that EVNT may subscribe to for
real-time coverage updates:

| Event topic | When fired | Payload `data` fields |
|---|---|---|
| `neuroassist-ai.acs.coverage.updated.v1` | After any link created, revoked, or caregiver activated/deactivated | `{ pacienteId, cuidadorId, action: 'linked'\|'unlinked'\|'deactivated', isCovered }` |

> NOTE: The ARTEMIS topic name above is **proposed**. Coordination with the EVNT team
> is required before `acs-service` publishes this event in production. This document
> freezes the FE-level contract only. The BE event catalog (`event-catalog.md`) must
> be updated separately by the acs-service team.

---

## Known Limitations

### N+1 Coverage Derivation (Performance)

The FE facade (`AcsCaregiverFacade._refreshCoverageMap()`) derives coverage by calling
`listForCaregiver()` for each active caregiver in sequence. This is O(N) requests per
coverage refresh, where N = number of active caregivers.

**Current status:** Acceptable in dev/mock (in-memory, no latency). Risk in production
with large rosters. Proposed fix: a batch endpoint `GET /v1/acs/patients/:id/coverage`
that returns `CoverageVm` directly. Tracked as: FE-ACS-032 (future sprint).

### RBAC Simplification (Security)

The FE guards use `token !== null` as the permission check (MVP simplification). Real
RBAC via JWT claims (coordinator role vs. cuidadora role) is deferred. Tracked as:
FE-ACS-031 (open evolution issue).

---

## Evolution

Any team that needs to change the eligibility rule or the exported `CoverageVm` shape
must:
1. Notify the EVNT module owner before merging.
2. Increment the document version field at the top of this file.
3. Update the BE event catalog if the `neuroassist-ai.acs.coverage.updated.v1` payload changes.
4. Create a migration issue if consumers need adaptation time.
