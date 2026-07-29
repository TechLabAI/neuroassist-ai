# EDGE — Integration Contracts (`edge-service`)

> Module: **EDGE — Integração de Dispositivo Edge** · Service: `edge-service`
> External integrations: the **edge agent** (inbound REST client) and **GEST** (outbound profile pointer).
> Inbound consumers: **PAC** (device validation) and **DASH** (liveness poll). No third-party external
> APIs in the MVP.

## 1. GEST — Detection-profile version pointer (outbound)

On each heartbeat, EDGE fetches the **active detection-profile version pointer** for the device's tenant
from GEST and piggybacks it in the heartbeat response (ADR-006, api-contract §3.1). EDGE **transports**
the pointer; GEST **authors** the profile. The device pulls the profile *content* directly from GEST with
its own device credential (GEST `api-contract.md` §4.4) — EDGE only carries the version number so the
device knows whether to re-pull.

| Aspect | Value |
|---|---|
| Service | `gest-service` (discovered via REG) |
| Endpoint | `GET /api/v1/gest/detection-profiles/active` |
| Auth | EDGE's own service credential (OAuth2 `client_credentials`, `tenant`-scoped) |
| Forwarded headers | `X-Correlation-Id` |
| Response | active profile with `version` + `X-Profile-Version`; `404 /errors/no-active-profile` |
| SLA | < 200 ms p99 (co-located microservices) |

### 1.1 Resilience

| Knob | Value |
|---|---|
| Read timeout | 2 000 ms |
| Connect timeout | 500 ms |
| Retry | 1 retry on `ConnectException` / `ReadTimeoutException` (idempotent GET) |
| Circuit breaker | Resilience4j; threshold 50% / 10-call window; wait 30 s before HALF_OPEN |
| Fallback | **degraded** — accept the heartbeat, omit the profile pointer, set `profilePointerDegraded: true` |

### 1.2 Behavior invariants (EARS)

- WHEN GEST returns an active profile, THE SYSTEM SHALL set `currentProfileVersion = version` and
  `profileDrift = (runningProfileVersion != version)` in the heartbeat response.
- WHEN GEST returns `404 /errors/no-active-profile`, THE SYSTEM SHALL still accept the heartbeat and set
  `currentProfileVersion: null`, `profileDrift: false`.
- IF GEST is unavailable (timeout / circuit OPEN), THEN THE SYSTEM SHALL still accept the heartbeat, set
  `profilePointerDegraded: true`, and SHALL NOT fail liveness — liveness never depends on GEST (doc 06,
  ADR-006).
- THE SYSTEM SHALL forward `X-Correlation-Id` to GEST on every call.
- THE SYSTEM SHALL NOT send any biometric or patient-identifying data to GEST — the only outbound context
  is the tenant (via the service token) and, where GEST requires it, the opaque `deviceId`.

## 2. PAC — Device validation (inbound)

PAC calls EDGE (`EdgeDevicePort`) at write time to validate `paciente.dispositivo_edge_id`
(cross-service reference, not a FK — ADR-005, doc 06 Critical Dependencies).

| Aspect | Value |
|---|---|
| Endpoint | `GET /api/v1/edge/patients/{pacienteId}/device` (or `GET /devices/{deviceId}`) |
| Auth | PAC's service credential (`edge.internal`) or a caregiver `edge.read` token |
| Response | paired device (with `pairingState`, `status`) or `404 /errors/patient-not-paired` |
| Contract guarantee | referential honesty (Framing invariant #5): a `200` means the device is registered and paired to that patient |

- WHEN PAC resolves a `dispositivo_edge_id`, THE SYSTEM SHALL return `200` only if the device is
  registered and (for an active pairing) currently `paired` to that same patient, else `404`.
- THE SYSTEM SHALL NOT require PAC to send any patient data beyond the id in the URL path.

## 3. DASH — Liveness poll (inbound)

DASH renders the caregiver's "monitoring status" and stale-device warning by **polling** EDGE (no push;
messaging disabled — doc 06 R1).

| Aspect | Value |
|---|---|
| Endpoint | `GET /api/v1/edge/devices/{deviceId}/liveness` / `GET /api/v1/edge/patients/{pacienteId}/liveness` |
| Auth | caregiver `edge.read` token |
| Cadence | advisory `poll-hint-seconds` (default 15 s, api-contract §5); ETag/`If-None-Match` → `304` |
| Response | `{ status, ultimaSincronizacao, versao, stalenessThresholdSeconds, serverTime }` |

- THE SYSTEM SHALL derive `status` at read time so DASH never shows a false `online` after the staleness
  window (doc 06 R2).
- THE SYSTEM SHALL support conditional GET (ETag keyed on the derived-status bucket) so an `online→offline`
  crossing always invalidates the cache even without a new heartbeat.

## 4. Edge agent — Registration / enrollment / heartbeat (inbound, in-product client)

The edge agent is a **separate deployable** on the patient's device — the only client of register-claim,
enroll, and heartbeat. It is an in-product process, **not** a third-party API (doc 04 External
Integrations). Contract is versioned (this document + api-contract) and served over REST/HTTPS via GWY.

- THE SYSTEM SHALL authenticate every steady-state agent call as the device (device token, api-contract
  §6.3); the id is never taken from the body (anti-spoofing, doc 06 R3).
- THE SYSTEM SHALL accept from the agent only liveness + version metadata — never media (Framing #6).

## 5. No third-party external integrations (MVP)

- No push-notification vendor and no MDM in the MVP (doc 04). Device credentialing uses the project's own
  OAuth2 boundary (ACS). Future candidates (remote alerting, fleet management, mTLS PKI) would be added
  here — see SPEC-06 "Future hardening".
