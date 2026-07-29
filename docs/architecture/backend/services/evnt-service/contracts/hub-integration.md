# EVNT — Hub / Inter-Module Integration (`evnt-service`)

> Module: **EVNT — Eventos e Alarmes** · Service: `evnt-service`
> **TechLab AI / Hub dependency: NONE** (`ai.hasAi = false`). Urgency is a deterministic policy on the
> `Comando`, not an inference — there is no orchestration/knowledge/document_composition/analytics call.
> This file therefore documents the **inter-module synchronous REST (Feign) contracts** EVNT depends on
> within NeuroAssist AI. No broker (messaging disabled) — every call below is synchronous REST via GWY,
> discovered via REG.

## 1. TechLab AI services consumed

| Bounded context | Used? | Rationale |
|---|---|---|
| orchestration | no | Urgency is a pure function of `Comando.urgencia`; no LLM routing. |
| knowledge | no | No retrieval/RAG in the alarm path. |
| document_composition | no | No document generation. |
| analytics | no | False-positive rate is computed from EVNT's own `evento` table (SPEC-02/ADR-006). |

**EARS**
- THE SYSTEM SHALL determine the notification tier solely from `evento.urgencia_snapshot`
  (`emergencia → full_screen_alarm`, `rotina → discreet`) with no external inference.

## 2. Inter-module Feign contracts

All calls carry `X-Correlation-Id` and the caller's tenant context; targets are resolved via Eureka (REG)
and routed via GWY. Timeouts + circuit breakers per SPEC-10.

### 2.1 `GestValidationPort` → GEST (`gest-service`)
- **Op:** `POST /api/v1/validations` — validate a `RecognizedCommandReport` against the referenced detection
  profile/primitives. Returns `{valid, reason?, profileVersion}`.
- **Headers:** `Authorization` (service token), `X-Correlation-Id`.
- **Fallback:** timeout → **fail closed for emergencies** (surface `502/504`, record failure); never create
  an Evento on an unvalidated report (Framing invariant #2).

### 2.2 `CommandLookupPort` → PAC (`pac-service`)
- **Op:** `GET /api/v1/comandos/{comandoRef}` — resolve `{label, urgencia, sensibilidade, pacienteId}`.
- **Fallback:** timeout on `emergencia` → fail closed + surface; a deleted/deactivated `Comando` →
  `422 command-unresolvable` (doc 06 assumption #1, contracted in SPEC-10).

### 2.3 `CaregiverRoutingPort` → ACS (`acs-service`)
- **Op:** `GET /api/v1/pacientes/{pacienteId}/responsavel` — resolve the responsible `Cuidador` (routing
  target). Also `GET /api/v1/cuidadores/{id}` to resolve `confirmadoPor` on the confirmation path.
- **Fallback:** timeout on `emergencia` → fail closed + surface (unroutable emergency is escalated/logged,
  never dropped). Escalation-to-secondary is an open item (doc 06 gap #3), out of MVP.

### 2.4 `NotificationDispatchPort` → DASH (`dash-service`)
- **Op:** `POST /api/v1/notifications` — deliver `{eventoId, tier, targetCaregiverId, commandLabel,
  urgencia}` for rendering. Returns delivery ack.
- **Fallback:** **fail open** — a failed delivery leaves the `notification_dispatch` `queued`/`failed` and is
  retried (an emergency is never lost to a transient DASH outage, ADR-005 / doc 06 risk #1). *Note:* the
  browser also learns via EVNT's own SSE `GET /events/stream` (api-contract §3.3); the DASH call is the
  server-to-server dispatch record, the SSE is the live browser channel.

### 2.5 `AuditTrailPort` → HIST (`hist-service`)
- **Op:** `POST /api/v1/audit-facts` — append `{factType, eventoId, actor?, at, payload}` for
  `EventoCriado`, `EventoConfirmado`, `FalsoPositivoMarcado`, dispatch failures.
- **Fallback:** retryable, **not on the emergency critical path** (doc 06 assumption #4) — an audit-append
  failure is retried without blocking the alarm dispatch.

## 3. Propagation headers

| Header | Value | Purpose |
|---|---|---|
| `X-Correlation-Id` | inbound or GWY-generated | end-to-end trace across GEST/PAC/ACS/DASH/HIST |
| `X-Tenant` (internal) | resolved `empresa` | tenant-scoped downstream lookups (never trusted from edge) |
| `Authorization` | service/caregiver token | downstream authZ |

**EARS — propagation**
- THE SYSTEM SHALL propagate `X-Correlation-Id` and the resolved tenant on every downstream Feign call and
  audit fact.
- WHILE processing an `emergencia`, IF GEST or PAC does not respond within its timeout, THEN THE SYSTEM SHALL
  fail closed and surface the failure rather than creating or notifying an Evento silently.

## 4. References

- ADR-005 (broker-deferred, synchronous), ADR-004 (system of record).
- `contracts/integration-contracts.md` (edge + external), `SPEC-10` (integrations), `SPEC-09` (messaging).
