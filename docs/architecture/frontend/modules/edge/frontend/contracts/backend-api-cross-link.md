# Backend API Cross-Link — EDGE (Frontend ↔ `edge-service`)

> This file **references** the frozen BE contract; it does not redefine it.
> Source of truth: `docs/architecture/backend/services/edge-service/contracts/`
> (`api-contract.md`, `data-model.md`, `event-contracts.md`). Base path `/api/v1/edge`, routed via GWY,
> discovered via REG. The browser never reaches `edge-service` directly.

## 1. What the FE binds to

| FE artifact | BE source | Binding |
|---|---|---|
| `DispositivoEdgeView` (device read shape) | `api-contract.md` §4.1 | **Verbatim** — `deviceId`, `tenantRef`, `pacienteId`, `pairingState`, `status`, `ultimaSincronizacao`, `versao`, `runningProfileVersion`, `currentProfileVersion`, `profileDrift`, `stalenessThresholdSeconds`, `serverTime`. Lives in `@neuroassist/shared/contracts`. |
| `RegisterDeviceResult` | `api-contract.md` §2.1 | `POST /devices` response — `deviceId`, `pairingState`, `label`, `enrollmentToken`, `enrollmentExpiresAt`, `createdAt`. The `enrollmentToken` is the one-time claim code shown in the pairing UI. |
| `LivenessView` | `api-contract.md` §4.4 | `GET …/liveness` response — `deviceId`, `pacienteId`, `status`, `ultimaSincronizacao`, `versao`, `stalenessThresholdSeconds`, `serverTime`. Poll target for the connectivity chip (ETag/304). |
| Pair / unpair / retire | `api-contract.md` §7.1, §7.2, §2.3 | `POST /devices/{id}/pairings`, `DELETE /devices/{id}/pairings/current`, `POST /devices/{id}:retire` — conductor (`edge.manage`). |
| `X-Staleness-Threshold-Seconds` header + `serverTime` | `api-contract.md` §1.1, §5 | The FE uses `serverTime − ultimaSincronizacao` as **server-relative age** and honors the BE staleness window as the default `offlineWindowMs`. |
| `ProblemDetail` error types | `api-contract.md` §8 | Mapped to `AppError` + pt-BR copy in SPEC-07. |

## 2. What the FE deliberately does NOT consume

- **`POST /heartbeat`** (`api-contract.md` §3.1) — the **edge agent's** call, authenticated with the device
  credential. The browser holds no device credential and posts no heartbeat.
- **`POST /devices/enroll`** (`api-contract.md` §2.2) — the **edge agent** exchanges the enrollment token for
  its credential. The FE only *observes* the effect (first heartbeat) via liveness polling.
- **`GET /devices/{deviceId}/tenant`** (`api-contract.md` §4.2) — GEST's service-credential device→tenant
  resolution, not a browser call.
- Any **credential secret** (`clientId`/`clientSecret`, §2.2) — never rendered, never stored client-side.

## 3. Superseded FE draft (endpoint reconciliation)

The FE vision doc 05 §2 sketched a draft API:
`POST /devices/pairing-codes`, `POST /devices/{id}/repair`, `DELETE /devices/{id}`, `GET /devices/{id}`.
This is **superseded** by the frozen BE contract:

| FE draft (vision doc 05 §2) | Frozen BE endpoint (authoritative) |
|---|---|
| `POST /devices/pairing-codes` (issue pairing code) | `POST /api/v1/edge/devices` → mints a one-time **enrollment token** (§2.1); the operator hands it to the agent, which enrolls via `POST /devices/enroll` (§2.2) |
| *(implicit "claim code" bound to patient)* | pairing to a patient is a **separate** operator call `POST /devices/{id}/pairings` `{pacienteId}` (§7.1) — register ≠ pair |
| `POST /devices/{id}/repair` | re-pair = unpair (`DELETE …/pairings/current`, §7.2) then a fresh register+pair (one active device per patient, §7.1) |
| `DELETE /devices/{id}` (unpair) | `DELETE /api/v1/edge/devices/{id}/pairings/current` (§7.2); hard-delete never happens — retire is `POST …:retire` (§2.3) |
| `GET /devices/{id}` (poll status) | `GET /api/v1/edge/devices/{deviceId}` (full) **or** the cache-friendly `GET …/liveness` (§4.4) for the chip poll |

The FE pairing flow (SPEC-03 §4, SPEC-04 §2) is re-anchored on: **register → show enrollment token → poll
for first heartbeat → pair-to-patient → confirm paired·online**. `data-access` MUST bind the frozen shapes;
the vision's draft endpoint names MUST NOT be used.

## 4. Semantic reconciliation — `status` (BE binary) vs derived state (FE 5-state)

- The BE derives `status ∈ {online, offline, n/a}` server-side at read time (§3.2) using its own
  `stalenessThresholdSeconds` (90 s). It has **no `stale`/`unknown`** band.
- The FE derives `online | stale | offline | unknown | not-paired` (SPEC-03 §2) from the **server-relative
  age** (`serverTime − ultimaSincronizacao`) and its own tunable flags, adding an **earlier `stale`** warning
  and a `unknown` on poll failure. FE `offline` defaults to the BE window so both agree on `offline`; FE
  never renders the raw `status` flag as truth (ADR-005 local).
- **If the BE later adds a dedicated `lastHeartbeatAt`** distinct from `ultimaSincronizacao` (doc 06 §1
  semantic-gap risk), the FE derivation must switch to it — isolated in `DeviceFacade.deriveState`.

## 5. Open cross-boundary items (carried to backlog)

- **Enrollment "enrolled" state visibility.** BE §2.2 guards re-enrollment by `pairingState ≠ registered`,
  but enrollment does not introduce a distinct wire state, so the FE cannot directly observe "agent claimed
  but not yet paired". The FE infers claim from the **first fresh heartbeat**; confirm with BE whether a
  `claimed`/`enrolled` signal (or `ultimaSincronizacao` on a `registered` device) is reliably pollable.
  Freeze in `/fe-architect-backlog`.
- **ACS ↔ EDGE dynamic client registration** (BE M2 warning): the device credential is issued by ACS's token
  endpoint (§6.3). This is agent-side and does not affect the browser, but the onboarding UX copy ("run the
  agent, enter this code") depends on the final enrollment ergonomics — confirm before backlog copy freeze.
- **Server-relative age guarantee.** The FE depends on `serverTime` being present in every liveness/heartbeat
  response (§1.1 header + body). Confirm `serverTime` is in the `LivenessView` body (not only the header) so
  the derivation never falls back to the client clock (doc 06 R5).
