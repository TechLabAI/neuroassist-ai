# EDGE — Event Contracts (`edge-service`)

> Module: **EDGE — Integração de Dispositivo Edge** · Service: `edge-service` · Package: `com.neuroassist.edge`
> **Messaging: disabled** (`stack.messaging.enabled = false`). There is **no broker** in the MVP:
> inter-service interaction is synchronous REST; the events below are **in-process domain events**.
> EARS notation (D-057) governs consume/publish behavior.

---

## 1. Messaging posture (MVP)

- THE SYSTEM SHALL NOT publish to or consume from any message broker in the MVP — there is none
  (`stack.messaging.enabled = false`).
- The domain events in §2 are raised **in-process** (Spring `ApplicationEventPublisher`) and used for
  local side effects (audit append, log/metric emission). They are **not** a public bus contract.
- Cross-service coordination happens over REST (`api-contract.md`): DASH **polls** liveness, PAC resolves
  a device, GEST resolves a device's tenant. EDGE cannot fan-out a "device offline" event; the derived
  `offline` state is discovered by DASH polling (ADR-006, doc 06 R1).
- **Extraction path (doc 04 §"Async vs Sync", ADR-006):** the events are named so that WHERE a broker is
  later introduced, **`DispositivoFicouOffline`** is the first candidate to move onto an async topic with
  **no rename** — becoming the platform's proactive "monitoring dropped" alert. Explicitly out of MVP scope.

---

## 2. In-process domain events

| Event | Raised when | In-process consumers | Future topic (deferred) |
|---|---|---|---|
| `DispositivoRegistrado` | a new `DispositivoEdge` obtains its backend identity | audit log, DASH cache hint | `edge.device.registered.v1` |
| `DispositivoPareado` | a device is bound to a patient (enables PAC's `dispositivo_edge_id`) | audit append, DASH cache hint | `edge.device.paired.v1` |
| `DispositivoDespareado` | a device is unbound (replacement/retirement) | audit append, cache invalidation | `edge.device.unpaired.v1` |
| `HeartbeatRecebido` | *anchor* — a paired device reports in | liveness refresh, drift check, metric | `edge.heartbeat.received.v1` |
| `DispositivoFicouOffline` | a liveness read observes `online → offline` (staleness crossed) | audit append, DASH cache hint | `edge.device.offline.v1` (**first to move**) |
| `DispositivoVoltouOnline` | a heartbeat resumes an offline device (`offline → online`) | audit append, DASH cache hint | `edge.device.online.v1` |
| `ProfileDriftDetected` | reported `runningProfileVersion` ≠ GEST current pointer | log/metric, DASH cache hint | `edge.profile.drift.v1` |

### 2.1 In-process event payloads (logical)

```json
// DispositivoPareado
{ "deviceId": "d7b1…", "pacienteId": "9c2a…", "tenantRef": "empresa-0001",
  "actor": "conductor:danilo", "at": "2026-07-29T14:00:00Z" }

// DispositivoFicouOffline  (derived at read time; audited when observed)
{ "deviceId": "d7b1…", "pacienteId": "9c2a…", "tenantRef": "empresa-0001",
  "ultimaSincronizacao": "2026-07-29T13:57:10Z", "stalenessThresholdSeconds": 90,
  "observedAt": "2026-07-29T14:00:30Z" }

// ProfileDriftDetected
{ "deviceId": "d7b1…", "runningProfileVersion": 6, "currentProfileVersion": 7,
  "at": "2026-07-29T14:00:30Z" }
```

- WHEN a pairing/lifecycle transition commits, THE SYSTEM SHALL raise the corresponding `Dispositivo*`
  event **post-commit**, so no consumer observes an uncommitted state.
- WHEN a liveness read observes a crossing, THE SYSTEM SHALL raise `DispositivoFicouOffline` /
  `DispositivoVoltouOnline` and append the matching `liveness` transition (data-model §2.4) idempotently
  — the same crossing SHALL NOT be double-audited within one read.
- IF the transaction rolls back, THEN THE SYSTEM SHALL NOT raise the event.

---

## 3. Liveness is a read-derived signal, not an event

Because liveness is **derived at read time** (ADR-006), `DispositivoFicouOffline` is not emitted by a
scheduler in the MVP — there is no persisted flag and no job to flip it (this is exactly what removes the
false-"online" risk, doc 06 R2). The transition is materialized only when **observed**: by a DASH poll
(api-contract §4.4) or by the next heartbeat. An optional periodic **sweep** for *proactive* alerting
(without a reader present) is deliberately deferred (doc 06, ADR-006 Consequences) to avoid a scheduler
dependency now; when added it reuses the same `DispositivoFicouOffline` fact.

- THE SYSTEM SHALL treat `DispositivoFicouOffline` as a fact derived from `ultima_sincronizacao` and the
  staleness threshold, consistent for every reader (Framing invariant #3).
- WHERE a future broker is enabled, THE SYSTEM SHALL publish `DispositivoFicouOffline` from the same
  observation point without renaming the event.

---

## 4. Idempotency & versioning

### 4.1 Idempotency

- THE SYSTEM SHALL treat the heartbeat (`POST /heartbeat`) as naturally idempotent for liveness: repeated
  heartbeats within a window simply re-stamp `ultima_sincronizacao`; only a `status` **crossing** appends
  an audit row (§2), so duplicate beats do not grow the audit trail.
- THE SYSTEM SHALL treat device→tenant, resolve-device, and liveness reads as idempotent (read-only).
- WHEN enrollment (`POST /devices/enroll`) is retried with an already-consumed token, THE SYSTEM SHALL
  respond `401 enrollment-invalid` (single-use) rather than issue a second credential (SPEC-05).

### 4.2 Payload versioning

- THE SYSTEM SHALL version the future async topics semantically (`.v1` suffix) so a broker rollout is
  additive.
- WHEN a backward-compatible field is added to a logical event payload, THE SYSTEM SHALL keep existing
  in-process consumers working (additive only).

---

## 5. Summary

| Contract | Kind | Direction | Transport | Persisted |
|---|---|---|---|---|
| `DispositivoRegistrado/Pareado/Despareado` | in-process domain event | internal | Spring app-events | audit (transition) |
| `HeartbeatRecebido` | in-process domain event | internal | Spring app-events | aggregate (latest) |
| `DispositivoFicouOffline/VoltouOnline` | read-derived fact | internal | Spring app-events | audit on crossing |
| `ProfileDriftDetected` | in-process domain event | internal | Spring app-events | no (log/metric) |
| Liveness signal (to DASH) | REST poll | EDGE → DASH | REST (`api-contract §4.4`) | n/a |
