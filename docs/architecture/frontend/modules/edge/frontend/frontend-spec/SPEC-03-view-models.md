# SPEC-03 — View Models — EDGE (Frontend)

> View-models are `record`-style readonly TypeScript shapes exposed as Signals by `DeviceFacade` and
> `PairingFacade`. Components read `.asReadonly()` signals; they never mutate.
>
> **Contract binding (authoritative):** the wire shapes are the frozen BE `edge-service` responses
> (`api-contract.md` §2–§4). The FE **derived connectivity** (`stale`/`unknown`) is a client-side
> computation over the BE's `status`, `ultimaSincronizacao`, `serverTime` and `stalenessThresholdSeconds`
> (ADR-005 local). The FE never persists nor invents a wire field.

## 1. Domain input — `DispositivoEdgeView` (from `GET /api/v1/edge/devices/{deviceId}`)

Verbatim mirror of the BE full-device response (api-contract §4.1). Lives in `@neuroassist/shared/contracts`.

```ts
export type PairingState = 'registered' | 'paired' | 'unpaired' | 'retired';
export type WireStatus   = 'online' | 'offline' | 'n/a';   // BE-derived, binary (api-contract §3.2)

export interface DispositivoEdgeView {
  readonly deviceId: string;
  readonly tenantRef: string;
  readonly pacienteId: string | null;
  readonly pairingState: PairingState;
  readonly status: WireStatus;                      // BE derived at read time — a HINT, not FE truth (ADR-005)
  readonly ultimaSincronizacao: string | null;      // ISO-8601 UTC; null ⇒ never reported
  readonly versao: string | null;
  readonly runningProfileVersion: number | null;
  readonly currentProfileVersion: number | null;
  readonly profileDrift: boolean;
  readonly stalenessThresholdSeconds: number;       // BE effective window (api-contract §5)
  readonly serverTime: string;                      // authoritative clock for freshness (avoids client skew)
}
```

## 2. Derived connectivity — `DerivedState` (FE, ADR-005)

```ts
export type DerivedState = 'online' | 'stale' | 'offline' | 'unknown' | 'not-paired';
export type ConnectivityTone = 'ok' | 'warn' | 'er' | 'muted';
```

Derivation rule (a `computed()` over the latest polled device + a clock signal), **server-relative age**
preferred to `now(browser) − ultimaSincronizacao` to avoid clock skew (doc 06 R5, ADR-005 Consequences):

```
age = serverTime − ultimaSincronizacao            (server-relative; from the same poll response)
pairingState ∈ {unpaired, retired} OR status='n/a'      → 'not-paired'   (muted; chip hidden/quiet)
pairingState=paired AND ultimaSincronizacao = null      → 'offline'      (paired, never reported — loud)
last poll failed                                        → 'unknown'      (degraded; NEVER 'online')
age < edge.stalenessThresholdMs        AND poll ok      → 'online'       (ok)
stalenessThresholdMs ≤ age < offlineWindowMs           → 'stale'        (warn — early, non-alarming)
age ≥ edge.offlineWindowMs                             → 'offline'      (er — loud)
```

- `edge.offlineWindowMs` defaults to the BE `stalenessThresholdSeconds × 1000` (90 s) so FE `offline`
  agrees with BE `status:'offline'`; `edge.stalenessThresholdMs` (45 s) opens an **earlier** `stale` band
  the BE does not express — the honest middle (ADR-005 rejected binary-only).
- **`unknown` is never `online`** (invariant). **`online` requires a fresh heartbeat AND a successful poll**
  — the raw BE `status:'online'` alone is never sufficient (ADR-005 / doc 03 §5 freshness gate).

## 3. Shell chip / Home VM — `DeviceConnectivityVM`

The single read-model every consumer (topbar, Home) reads (doc 03 §2, doc 05 §1).

```ts
export interface DeviceConnectivityVM {
  readonly paired: boolean;
  readonly derivedState: DerivedState;
  readonly lastSyncIso: string | null;
  readonly lastSyncRelative: string;                // "há 2 min" (pt-BR) via RelativeTimeComponent
  readonly versao: string | null;
  readonly tone: ConnectivityTone;                  // ok | warn | er | muted (design tokens, SPEC design-tokens)
  readonly label: string;                           // pt-BR ("Dispositivo online" / "…sem sinal" / …)
}
```

## 4. Pairing VM — `PairingVM`

```ts
export type PairingPhase =
  | 'idle' | 'registering' | 'awaiting-claim' | 'awaiting-first-heartbeat'
  | 'paired' | 'expired' | 'error';

export interface PairingVM {
  readonly phase: PairingPhase;
  readonly deviceId: string | null;
  readonly enrollmentToken: string | null;          // one-time claim code (BE api-contract §2.1); shown once
  readonly enrollmentExpiresAtIso: string | null;   // TTL boundary; drives the countdown
  readonly pacienteId: string | null;               // pairing target (from pacienteHint / onboarding)
  readonly versao: string | null;                   // agent version reported at first heartbeat
  readonly derivedState: DerivedState;              // computed once the device begins reporting
  readonly errorMessage: string | null;             // pt-BR user-facing (SPEC-07)
}
```

## 5. Device detail VM — `DeviceDetailVM`

```ts
export interface DeviceDetailVM {
  readonly device: DispositivoEdgeView;
  readonly derivedState: DerivedState;
  readonly lastSyncRelative: string;
  readonly profileDrift: boolean;                   // running ≠ current profile version (api-contract §3/§4)
  readonly canManage: boolean;                      // conductor entitlement (SPEC-05) — gates action buttons
}
```

## 6. Outcome emitted to host — `PairedDeviceOutcome`

```ts
export interface PairedDeviceOutcome {
  readonly deviceId: string;
  readonly pacienteId: string;
  readonly versao: string | null;
  readonly derivedState: DerivedState;              // guaranteed 'online' only after a real first heartbeat
}
```

`derivedState: 'online'` in the outcome is emitted **only** after a genuine first heartbeat made the device
fresh — the onboarding host can trust "paired · online" is real, never a hopeful local flip (ADR-005,
doc 05 §4).

## 7. VM state invariants

| Invariant | Rule |
|---|---|
| Freshness gate | `derivedState = 'online'` **only if** server-relative `age < stalenessThresholdMs` AND last poll succeeded. Raw `status='online'` alone is never sufficient (ADR-005). |
| Unknown ≠ online | On poll failure, `derivedState = 'unknown'` (degraded), never optimistically `'online'` (doc 03 §5). |
| No premature paired | `PairingPhase` reaches `'paired'` **only after** a real first heartbeat (`ultimaSincronizacao ≠ null` AND fresh), not merely after the enrollment token is claimed (doc 03 §5). |
| Code TTL respected | An expired `enrollmentToken` cannot be reused; `phase → 'expired'` forces a re-register rather than hanging in `awaiting-claim` (api-contract §2.1, doc 03 §5). |
| Single active device | At most one `paired` `DispositivoEdge` per patient (MVP); re-pairing supersedes the previous bind explicitly (api-contract §7, doc 03 §5). |
| No credential/biometric in VM | No `clientSecret`, video, image, landmark, or raw confidence ever enters a VM or the DOM (api-contract §8, privacy invariant doc 02). |
| Read-only for caregivers | Monitoring VMs require no privilege; `canManage`/action outputs require the conductor entitlement (doc 03 §5). |

## 8. UI events the VM exposes (component → facade)

| Event | Facade method | State effect |
|---|---|---|
| `RequestRegister` | `PairingFacade.register(label, pacienteHint?)` | `idle → registering → awaiting-claim` (mint enrollment token) |
| `PairToPatient` | `PairingFacade.pairToPatient(pacienteId)` | after claim → `awaiting-first-heartbeat` |
| `PairingCodeExpired` | *(internal countdown)* | `awaiting-claim → expired` |
| `FirstHeartbeatReceived` | *(internal poll)* | `awaiting-first-heartbeat → paired` |
| `ConnectivityPolled` | *(internal poll tick)* | recompute `derivedState` |
| `RePairRequested` | `DeviceFacade` → `PairingFacade.register(...)` | starts a fresh pairing (supersedes bind) |
| `UnpairRequested` | `DeviceFacade.unpair(reason)` | `paired → unpaired` (server result, not optimistic) |
| `RetireRequested` | `DeviceFacade.retire()` | `* → retired` (terminal) |

## Critérios de aceite (EARS)

- THE SYSTEM SHALL derive connectivity from the server-relative age of `ultimaSincronizacao` and SHALL NOT render the persisted `status` flag as current truth.
- WHILE the last connectivity poll failed, THE SYSTEM SHALL set `derivedState` to `unknown` and SHALL NOT set it to `online`.
- WHILE a paired device has a server-relative heartbeat age below the staleness threshold, THE SYSTEM SHALL set `derivedState` to `online`.
- WHEN the server-relative heartbeat age reaches the offline window, THE SYSTEM SHALL set `derivedState` to `offline`.
- WHEN the server-relative heartbeat age is between the staleness threshold and the offline window, THE SYSTEM SHALL set `derivedState` to `stale`.
- THE SYSTEM SHALL advance the pairing phase to `paired` only after a real first heartbeat and SHALL NOT advance it when only the enrollment token has been claimed.
- IF the enrollment token TTL elapses before a claim, THEN THE SYSTEM SHALL set the pairing phase to `expired` and SHALL require a new registration.
- THE SYSTEM SHALL NOT include any credential secret, video, image, or landmark data in any view-model.
