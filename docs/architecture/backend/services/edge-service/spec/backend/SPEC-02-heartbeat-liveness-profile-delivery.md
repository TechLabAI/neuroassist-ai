# SPEC-02 — Heartbeat, Liveness & Profile-Pointer Delivery (`edge-service`)

> **Module:** EDGE — Integração de Dispositivo Edge (**core / anchor**)
> **ADR refs:** ADR-006 (heartbeat pull-based, read-derived liveness, profile piggyback)
> **EARS guide:** `docs/architecture/backend/standards/ears-notation.md`

## 1. Objective

Specify the anchor behavior of EDGE: accepting heartbeats, deriving liveness deterministically at read
time, and delivering the current detection-profile version pointer with drift detection. This is the
recurring core the whole module exists to make trustworthy (brief §8).

## 2. Scope

**In:**
- `POST /heartbeat` ingestion (device-authenticated), server-clock stamping
- Read-time liveness derivation (`online`/`offline`/`n/a`) and the staleness parameters
- Liveness query endpoints (device / patient) with ETag/`304`
- Profile version pointer fetch from GEST + drift computation, with GEST-degraded fallback

**Out:**
- Pairing lifecycle (SPEC-03); registration/enrollment (SPEC-11)
- Device credential mechanism (SPEC-06); DDL (SPEC-04)

## 3. Technical decisions

| Decision | ADR / ref |
|---|---|
| Liveness derived at read time; no persisted `status` flag | ADR-006 / doc 06 R2 |
| `ultima_sincronizacao` stamped with server clock, not client | ADR-006 / doc 06 R5 |
| Profile pointer piggybacked on heartbeat; device pulls content from GEST | ADR-006 |
| Heartbeat succeeds even when GEST is down (degraded pointer) | doc 06 / integration-contracts §1.2 |

## 4. Operational parameters (defaults, configurable — closes doc 06 G2/G3)

| Parameter | Config key | Default |
|---|---|---|
| Heartbeat interval | `neuroassist.edge.heartbeat.interval-seconds` | `30` |
| Staleness factor | `neuroassist.edge.heartbeat.staleness-factor` | `3` |
| Staleness threshold (derived) | — | `90` s |
| DASH poll hint | `neuroassist.edge.liveness.poll-hint-seconds` | `15` |

## 5. Heartbeat behavior (EARS)

- WHEN a heartbeat is accepted, THE SYSTEM SHALL stamp `ultima_sincronizacao` with the server clock.
- WHEN a heartbeat is accepted, THE SYSTEM SHALL record `versao = agentVersion` and
  `running_profile_version` (latest value only).
- THE SYSTEM SHALL derive the acting `deviceId` from the device token, never the body (invariant #2).
- IF the body carries a `deviceId` differing from the token identity, THEN THE SYSTEM SHALL respond
  `403 Forbidden` (`type: /errors/identity-mismatch`).
- IF the authenticated device is `retired`, THEN THE SYSTEM SHALL respond `409 /errors/device-retired`.
- IF the heartbeat body carries any video/image/landmark field, THEN THE SYSTEM SHALL respond
  `400 /errors/validation` — privacy invariant #6.
- WHEN a heartbeat resumes a device that read `offline`, THE SYSTEM SHALL append a `liveness` transition
  `offline → online` (actor `system`) and raise `DispositivoVoltouOnline`.

## 6. Liveness derivation (EARS)

- THE SYSTEM SHALL compute `status = online` WHILE `serverNow − ultima_sincronizacao ≤ stalenessThreshold`,
  else `offline` — a pure function (invariant #3).
- WHILE a paired device has never sent a heartbeat, THE SYSTEM SHALL report `status = offline`.
- WHERE a device is `unpaired` or `retired`, THE SYSTEM SHALL report `status = "n/a"`.
- WHEN a liveness read observes an `online → offline` crossing, THE SYSTEM SHALL append a `liveness`
  transition (actor `system`) and raise `DispositivoFicouOffline` — no silent change (invariant #4).
- THE SYSTEM SHALL include `stalenessThresholdSeconds` and `serverTime` in every liveness response.
- WHEN `If-None-Match` matches the current status-bucket ETag, THE SYSTEM SHALL respond `304`; the ETag
  SHALL incorporate the derived-status bucket so an `online→offline` crossing invalidates it even without
  a new heartbeat.

## 7. Profile pointer & drift (EARS)

- WHEN a heartbeat is accepted, THE SYSTEM SHALL fetch the active profile version from GEST
  (`DetectionProfilePort`) and set `currentProfileVersion` + `profileDrift = (runningProfileVersion !=
  currentProfileVersion)`.
- WHEN `profileDrift` is true, THE SYSTEM SHALL raise `ProfileDriftDetected`.
- WHEN GEST reports no active profile, THE SYSTEM SHALL set `currentProfileVersion: null`,
  `profileDrift: false`.
- IF GEST is unavailable (timeout / circuit OPEN), THEN THE SYSTEM SHALL still accept the heartbeat, omit
  the pointer, and set `profilePointerDegraded: true` — liveness never depends on GEST.

## 8. Edge cases

| Scenario | Behavior |
|---|---|
| Heartbeat exactly at threshold boundary (age = 90 s) | `online` (≤ threshold) |
| Heartbeat age = 91 s | `offline` |
| Device offline, no reader ever polls | no proactive alert in MVP (sweep deferred, doc 06) |
| Clock-skewed client timestamp in body | ignored; server clock authoritative |

## 9. Acceptance criteria

- A device heartbeating within 90 s always reads `online`; one silent for > 90 s always reads `offline`.
- `ultima_sincronizacao` reflects the server time of accept, not any client-supplied timestamp.
- A heartbeat with a mismatched body `deviceId` is rejected `403`.
- With GEST down, a heartbeat still returns `200` with `profilePointerDegraded: true` and correct liveness.
- An `online→offline` crossing produces exactly one `liveness` audit row when first observed.

## 10. BDD coverage

`tests/cucumber/heartbeat-liveness.feature`, `tests/cucumber/profile-pointer-drift.feature`.
