# SPEC-04 — Data Access — EDGE (Frontend)

> `libs/edge/data-access` owns one thin HTTP service (`EdgeHttpService`, over
> `@neuroassist/shared/access-http-base` → GWY) and two signal facades (`DeviceFacade`, `PairingFacade`).
> The polling `timer` is internal to `DeviceFacade`. `data-access` may depend **only** on
> `shared/contracts`, `access-http-base`, `empresa-context`, `error-model` (nx-boundaries, doc 04 §4).
>
> **Endpoint reconciliation (authoritative):** the FE vision doc 05 §2 sketched
> `POST /devices/pairing-codes`, `POST /devices/{id}/repair`, `DELETE /devices/{id}`. Those are **superseded**
> by the frozen BE `edge-service` contract (base `/api/v1/edge`): registration mints a one-time
> **enrollment token** (`POST /devices`), pairing to a patient is a separate operator call
> (`POST /devices/{id}/pairings`), and liveness is a dedicated cache-friendly poll
> (`GET /patients/{pacienteId}/liveness`). See `contracts/backend-api-cross-link.md`.

## 1. `EdgeHttpService` (`libs/edge/data-access`)

Thin transport over the shared gateway client. All calls carry the session Bearer JWT (human/conductor);
`tenantId` is derived server-side from the token, never sent (api-contract §1.1). `X-Correlation-Id` is
attached by the shared client.

```ts
class EdgeHttpService {
  // Operator / conductor (edge.manage)
  register(body: { label: string; pacienteHint?: string }): Observable<RegisterDeviceResult>;   // POST /devices
  pairToPatient(deviceId: string, body: { pacienteId: string }): Observable<DispositivoEdgeView>; // POST /devices/{id}/pairings
  unpair(deviceId: string, body: { reason: string }): Observable<void>;                          // DELETE /devices/{id}/pairings/current
  retire(deviceId: string): Observable<DispositivoEdgeView>;                                      // POST /devices/{id}:retire

  // Read (edge.read)
  getDevice(deviceId: string): Observable<DispositivoEdgeView>;                                   // GET /devices/{deviceId}
  resolveDeviceForPatient(pacienteId: string): Observable<DispositivoEdgeView>;                   // GET /patients/{pacienteId}/device
  pollPatientLiveness(pacienteId: string, etag?: string): Observable<LivenessView | NotModified>; // GET /patients/{pacienteId}/liveness
  pollDeviceLiveness(deviceId: string, etag?: string): Observable<LivenessView | NotModified>;    // GET /devices/{deviceId}/liveness
}
```

`RegisterDeviceResult` mirrors api-contract §2.1 (`deviceId`, `pairingState`, `label`, `enrollmentToken`,
`enrollmentExpiresAt`, `createdAt`). `LivenessView` mirrors §4.4 (`deviceId`, `pacienteId`, `status`,
`ultimaSincronizacao`, `versao`, `stalenessThresholdSeconds`, `serverTime`). All DTOs live in
`@neuroassist/shared/contracts`.

> **Never consumed by the FE:** `POST /heartbeat` and `POST /devices/enroll` are the **edge agent's** calls
> (device credential), and `GET /devices/{deviceId}/tenant` is GEST's service-credential call (api-contract
> §3, §2.2, §4.2). The browser holds no device credential and posts no heartbeat.

## 2. `PairingFacade` (signals — owns the pairing flow)

```ts
class PairingFacade {
  private readonly _vm = signal<PairingVM>(idleVm());
  readonly state     = this._vm.asReadonly();
  readonly phase     = computed(() => this._vm().phase);
  readonly countdown = computed(() => secondsUntil(this._vm().enrollmentExpiresAtIso));

  register(label: string, pacienteHint?: string): void;  // POST /devices → show token, phase=awaiting-claim, start claim-poll
  pairToPatient(pacienteId: string): void;               // POST /devices/{id}/pairings → phase=awaiting-first-heartbeat
  retry(): void;                                          // re-issue the last failed step
  reset(): void;                                          // discard token + stop polling → idle
  stopPolling(): void;                                    // teardown (ngOnDestroy)
}
```

- **Claim detection.** After `register`, the facade polls `getDevice(deviceId)` / liveness on the poll
  cadence: a first non-null `ultimaSincronizacao` that is **fresh** (server-relative age < threshold) means
  the agent enrolled and reported → advance `awaiting-first-heartbeat → paired`. It never flips to `paired`
  on the enrollment response alone (ADR-005 no-premature-paired).
- **Countdown.** `countdown()` is derived from `enrollmentExpiresAtIso`; at zero, `phase → expired` and the
  poll stops (code TTL respected).
- **No optimism.** Pairing reflects the server result; on failure the previous phase is restored and the
  error surfaced (doc 05 §4).

## 3. `DeviceFacade` (signals — owns connectivity + detail + the poll timer)

```ts
class DeviceFacade {
  private readonly _device = signal<DispositivoEdgeView | null>(null);
  private readonly _clock  = signal<number>(Date.now());   // ticks with each poll response
  private readonly _pollOk = signal<boolean>(true);

  readonly device        = this._device.asReadonly();
  readonly derivedState  = computed<DerivedState>(() => deriveState(this._device(), this._pollOk(), FLAGS));
  readonly connectivity  = computed<DeviceConnectivityVM>(() => toConnectivityVm(this._device(), this.derivedState()));
  readonly detail        = computed<DeviceDetailVM>(() => toDetailVm(this._device(), this.derivedState(), this.canManage()));
  readonly lastSyncRelative = computed(() => relative(this._device()?.ultimaSincronizacao));

  load(deviceId: string): void;                 // GET /devices/{deviceId}
  resolveForActivePatient(): void;              // GET /patients/{pacienteId}/device (active empresa-context)
  startPolling(): void;                         // timer(pollIntervalMs) → switchMap liveness poll; paused when tab hidden
  stopPolling(): void;                          // teardown
  unpair(reason: string): void;                 // DELETE …/pairings/current → refresh device
  retire(): void;                               // POST …:retire → refresh device
}
```

- **Polling is the one place RxJS earns its keep** (doc 04 §6): `timer(pollIntervalMs)` → `switchMap` to the
  liveness poll, feeding `_device` + `_clock` + `_pollOk`. It **pauses** when the tab is hidden (Page
  Visibility) and resumes on focus; it stops on navigation away (doc 05 §3). Poll cadence and thresholds are
  feature flags (SPEC-05).
- **ETag / 304.** Liveness polls send `If-None-Match`; a `304 Not Modified` leaves `_device` unchanged but
  still ticks `_clock` + sets `_pollOk=true`, so `derivedState` keeps aging honestly toward `stale/offline`
  even without a body (api-contract §4.4 — the ETag incorporates the derived-status bucket boundary, so an
  online→offline crossing always invalidates it).
- **Poll failure** (network/5xx/timeout) sets `_pollOk=false` → `derivedState='unknown'`; a silent retry
  fires on the next tick (never assumed `online`, doc 05 §5).
- **Single source.** `connectivity` is the readonly signal the Shell reads via the integration port
  (SPEC-06); no consumer re-derives `online` from a raw flag (ADR-005).

## 4. Freshness derivation (`deriveState`)

`deriveState` implements SPEC-03 §2 using **server-relative age** (`serverTime − ultimaSincronizacao`) from
the same poll response — never `Date.now() − ultimaSincronizacao` — to defeat client clock skew (doc 06 R5).
`_clock` only advances the "how long since the last successful poll" component used to detect a **stalled
poll loop** (no fresh `serverTime` for > offlineWindow ⇒ treat as `unknown`).

## 5. Master resource table

| Resource | Endpoint | Service | Facade | Operations | Cache? |
|---|---|---|---|---|---|
| `Device` (register) | `POST /api/v1/edge/devices` | `EdgeHttpService` | `PairingFacade` | `register` | No |
| `Device` (pair) | `POST /api/v1/edge/devices/{id}/pairings` | `EdgeHttpService` | `PairingFacade` | `pairToPatient` | No |
| `Device` (unpair) | `DELETE /api/v1/edge/devices/{id}/pairings/current` | `EdgeHttpService` | `DeviceFacade` | `unpair` | No |
| `Device` (retire) | `POST /api/v1/edge/devices/{id}:retire` | `EdgeHttpService` | `DeviceFacade` | `retire` | No |
| `Device` (full) | `GET /api/v1/edge/devices/{deviceId}` | `EdgeHttpService` | `DeviceFacade` | `getDevice` | No (poll-fresh) |
| `Device` (resolve for patient) | `GET /api/v1/edge/patients/{pacienteId}/device` | `EdgeHttpService` | `DeviceFacade` | `resolveDeviceForPatient` | No |
| `Liveness` (by patient) | `GET /api/v1/edge/patients/{pacienteId}/liveness` | `EdgeHttpService` | `DeviceFacade` (poll) | `pollPatientLiveness` | **ETag/304** — bandwidth-friendly, never stale-cached across the bucket boundary |
| `Liveness` (by device) | `GET /api/v1/edge/devices/{deviceId}/liveness` | `EdgeHttpService` | `DeviceFacade` (poll) | `pollDeviceLiveness` | **ETag/304** |

DTOs consumed from `@neuroassist/shared/contracts`: `DispositivoEdgeView`, `RegisterDeviceResult`,
`LivenessView`. Error mapping via `@neuroassist/shared/error-model` (SPEC-07).

## Critérios de aceite (EARS)

- WHEN `DeviceFacade.startPolling` runs, THE SYSTEM SHALL poll the liveness endpoint on the configured cadence and SHALL pause polling while the browser tab is hidden.
- WHEN a liveness poll returns `304 Not Modified`, THE SYSTEM SHALL keep the last device snapshot and SHALL continue aging the derived connectivity state.
- IF a liveness poll fails with a network or 5xx error, THEN THE SYSTEM SHALL set the derived connectivity state to `unknown` and SHALL retry on the next poll tick without asserting `online`.
- WHEN `PairingFacade.register` succeeds, THE SYSTEM SHALL surface the one-time enrollment token exactly once and SHALL start polling for the first heartbeat.
- THE SYSTEM SHALL derive the acting device connectivity from the server-relative heartbeat age and SHALL NOT post any heartbeat or hold any device credential in the browser.
- WHEN an unpair operation returns success, THE SYSTEM SHALL refresh the device read-model rather than optimistically flipping local state.
- THE SYSTEM SHALL send `If-None-Match` on liveness polls and SHALL NOT cache a liveness response across an online-to-offline bucket boundary.
