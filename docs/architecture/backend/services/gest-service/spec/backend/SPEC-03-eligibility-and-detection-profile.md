# SPEC-03 — Eligibility Guard and Detection Profile (`gest-service`)

> **Module:** GEST — Detecção de Gesto (Edge)
> **ADR refs:** ADR-006 (invariants #1, #2, #3), ADR-005 BE
> **EARS guide:** `docs/architecture/backend/standards/ears-notation.md`

## 1. Objective

Specify the two remaining GEST domain capabilities: the eligibility check (safety gate for
emergency commands) and the detection profile lifecycle (versioned, immutable bundles pulled by
the edge engine). Together they enforce the safety-critical invariants identified in the vision phase.

## 2. Scope

**In:**
- `POST /eligibility:check` — safety guard called synchronously by PAC before creating a `Comando`
- Detection profile publish, retrieval, and conditional-GET edge pull
- Invariants #1 (eligibility), #2 (profile includes only supported), #3 (monotonic version)
- Profile immutability and single-active constraint

**Out:**
- TipoGesto state machine (SPEC-02)
- Report validation (SPEC-05)
- Device credential auth (deferred — SPEC-06 + EDGE team)

## 3. Technical decisions

| Decision | Ref |
|---|---|
| Eligibility is read-only / stateless; no side effects on check | api-contract §3 |
| Profile assembly snapshots `supported` primitives + their `engineFeatureRef` at publish time | data-model §2.4 |
| Profile version is a monotonic `bigint` per tenant; allocated by DB sequence | data-model §2.3 |
| At most one active profile per tenant — enforced by partial unique index `ux_profile_single_active` | data-model §2.3 |
| Edge pull uses ETag (`X-Profile-Version`) for bandwidth-efficient polling | api-contract §4.4 |

## 4. Eligibility Guard

### 4.1 Business logic

PAC calls `/eligibility:check` with `urgencia` and a list of `primitiveIds`. The check is
**pure read** — no state mutations, no persisted result.

```
POST /eligibility:check
  -> resolve all primitiveIds in tenant schema -> 422 if any unknown
  -> IF urgencia = 'emergencia':
       eligible = ALL primitives.detectabilityState == 'supported'
  -> IF urgencia = 'rotina':
       eligible = ALL primitives.detectabilityState != 'deprecated'
  -> return { eligible, urgencia, results[] }
```

### 4.2 Invariants (EARS)

- WHEN `urgencia = emergencia`, THE SYSTEM SHALL return `eligible: true` only if every primitive
  is in state `supported` — invariant #1.
- WHEN `urgencia = rotina`, THE SYSTEM SHALL return `eligible: true` if every primitive exists
  and is not `deprecated`.
- IF any `primitiveId` does not exist in the caller's tenant, THEN THE SYSTEM SHALL respond
  `422 Unprocessable Entity` (`type: /errors/unknown-primitive`) listing all offending ids.
- THE SYSTEM SHALL treat this endpoint as read-only (no state mutation, idempotent).

## 5. Detection Profile

### 5.1 Publish flow

```
POST /detection-profiles
  -> WHERE caller lacks gest.manage -> 403
  -> load all supported TipoGesto for tenant -> IF empty -> 422 /errors/empty-profile
  -> allocate next monotonic version (DB sequence per tenant)
  -> merge classificationParams (request overrides or tenant calibration defaults)
  -> set active = true on new profile; set active = false on all prior profiles
  -> snapshot detection_profile_primitive rows (tipo_gesto_id, nome_snapshot, engine_feature_ref)
     for every supported primitive
  -> raise DetectionProfilePublished (post-commit)
  -> 201 + Location header + body
```

### 5.2 Retrieval

- `GET /detection-profiles/active` — returns the single `active = true` profile; `404` if none.
- `GET /detection-profiles/{version}` — returns the exact immutable profile published at that
  version; `404` if not found in tenant.
- `GET /devices/{deviceId}/detection-profile` — validates device/tenant pairing via EDGE at
  request time; returns active profile; supports conditional GET (ETag = version as string).

### 5.3 Immutability

WHEN a profile is published, none of its content columns (`classification_params`,
`detection_profile_primitive` snapshot) may be mutated. Only `active` may flip to `false` when
superseded. THE SYSTEM SHALL reject any `UPDATE` of profile content via optimistic-lock or by
having no mutation path in the application layer.

### 5.4 Invariants (EARS)

- WHEN a profile is published, THE SYSTEM SHALL include only primitives in state `supported` at
  publish time — invariant #2.
- THE SYSTEM SHALL assign `version` strictly monotonic per tenant and SHALL never reuse a value
  (`ux_profile_version`) — invariant #3.
- WHEN a new profile becomes active, THE SYSTEM SHALL set every prior profile of the tenant to
  `active: false` without mutating their content.
- IF the tenant has zero `supported` primitives at publish time, THEN THE SYSTEM SHALL respond
  `422 Unprocessable Entity` (`type: /errors/empty-profile`) and SHALL NOT create a version.
- THE SYSTEM SHALL keep at most one `active = true` profile per tenant at any instant
  (`ux_profile_single_active`).
- WHEN the edge request carries `If-None-Match` equal to the active profile ETag (version), THE
  SYSTEM SHALL respond `304 Not Modified` with no body.

## 6. Edge cases

| Scenario | Behavior |
|---|---|
| Eligibility: mix of known + unknown primitiveIds | 422 with full list of unknown ids |
| Profile publish: concurrent publish from two sessions | Second TX fails on `ux_profile_version` / `ux_profile_single_active`; retry on optimistic lock |
| Edge pull: device in different tenant | 403 `/errors/device-not-paired` |
| No active profile when edge pulls | 404 `/errors/no-active-profile` |
| Profile published while prior profile is already deactivated | No-op deactivation (already false); proceed normally |

## 7. Acceptance criteria

1. `/eligibility:check` with `urgencia=emergencia` → `eligible=true` only if all primitives are `supported`.
2. `/eligibility:check` with unknown primitiveId → `422` listing the unknown ids.
3. Profile publish with zero `supported` primitives → `422 /errors/empty-profile`.
4. Profile publish: second profile has `version` > first; `version` never reused.
5. After second publish: only the latest profile has `active=true`.
6. Profile content retrieved at version N matches exactly what was snapshotted at publish time.
7. Conditional GET with matching ETag → `304`.
8. Device from different tenant on edge pull → `403`.

## 8. BDD coverage

`tests/cucumber/eligibility-check.feature` — eligibility scenarios.
`tests/cucumber/detection-profile.feature` — profile lifecycle, ETag, invariants.
