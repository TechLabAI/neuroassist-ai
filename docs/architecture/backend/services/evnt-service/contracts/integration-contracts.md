# EVNT — External Integration Contracts (`evnt-service`)

> Module: **EVNT — Eventos e Alarmes** · Service: `evnt-service`
> External (non-NeuroAssist-module) systems EVNT integrates with. Inter-module (GEST/PAC/ACS/DASH/HIST)
> contracts live in `hub-integration.md`.

## 1. Edge agent (inbound)

The only external system EVNT talks to directly.

| Aspect | Contract |
|---|---|
| Direction | inbound — edge → EVNT |
| Transport | REST/HTTPS, routed via GWY (`POST /api/v1/reports`) |
| Payload | `RecognizedCommandReport` (schema owned by GEST detection contract §3.1) |
| Auth | **Device-scoped token** — shape jointly owned with EDGE, **still open** (doc 06 gap #1). Assumed JWT-shaped (`deviceId` + `tenant` + `paciente` claims) until EDGE freezes mTLS vs OAuth2 `client_credentials`. |
| Idempotency | client-generated `reportId` (UUID); the edge **retries** on transient failure (local outbox), so ingress must be idempotent (ADR-004). |
| Rate / SLA | pilot scale (one patient, one edge); no formal rate limit in MVP. Emergency path budgeted for sub-second server processing after validation. |
| Fallback (edge side) | local retry outbox with backoff; EVNT's `200` on duplicate lets the edge stop retrying safely. |
| Privacy | payload MUST carry no image/frame/landmark data; EVNT rejects such bodies with `400` (privacy invariant). |

**EARS — edge integration**
- WHEN the edge posts a report with a device token whose `tenant`/`paciente` claims resolve consistently,
  THE SYSTEM SHALL accept it for validation.
- IF the device token is missing, expired, or its tenant ≠ the report's patient tenant, THEN THE SYSTEM
  SHALL reject with `401`/`403` and create no Evento.
- WHILE the edge retries a previously-accepted `reportId`, THE SYSTEM SHALL return the original verdict
  (`200`) so the edge's outbox drains without duplicating events.

## 2. Third-party notification channels (out of MVP)

Push/SMS/e-mail providers are **out of scope** (doc 06 risk #5). The MVP delivery channel is the in-app
caregiver panel (DASH render + EVNT SSE stream). Named here so the pilot's operating assumption is explicit:
an inattentive caregiver not looking at the panel is a known, accepted MVP risk; out-of-band escalation is
deferred.

**EARS**
- THE SYSTEM SHALL, in the MVP, deliver notifications only via the in-app panel channel (DASH + SSE) and
  SHALL NOT call any third-party push/SMS/e-mail provider.

## 3. Resilience posture (external)

| Call | Timeout | Breaker | On failure |
|---|---|---|---|
| edge → EVNT ingress | n/a (inbound) | — | edge retries via its outbox |

Downstream (GEST/PAC/ACS/DASH/HIST) resilience is specified in `hub-integration.md` §2 and SPEC-10.

## 4. References

- doc 06 (gaps: device-token auth, escalation), ADR-004, ADR-005.
- `contracts/api-contract.md` §3.1/§5, `SPEC-06` (security), `SPEC-10` (integrations).
