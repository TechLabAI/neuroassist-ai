# 03 — Domain Framing (Frontend) — EDGE

> Mirror of the backend domain — this document does **not** redefine the language.
> Canonical terms come from `_brief.md`, the project data-model
> (`_extensions.data_model`, entity `DispositivoEdge`) and, once generated, the
> EDGE backend `03-domain-framing`. Frontend framing covers only view-level shape.

## 1. Ubiquitous language (frontend mirror)

| Term | Meaning (frontend view) | Source of truth |
|---|---|---|
| **DispositivoEdge** | The local agent record: `{ id, pacienteId, status, ultimaSincronizacao, versao }` | edge-service (BE) |
| **Pairing code** | Short-lived token issued by edge-service; the agent claims it to bind to the patient | edge-service |
| **Heartbeat** | Periodic report from the agent recorded as `ultimaSincronizacao` | edge-service |
| **status** | Persisted enum `online \| offline` on the record | edge-service |
| **Derived connectivity** | FE-computed `online \| offline \| stale/unknown` from heartbeat freshness | **EDGE-FE (this module, ADR-005)** |
| **Staleness threshold** | Max heartbeat age still considered `online` | EDGE-FE config |
| **Paciente** | The patient a device is bound to (referenced, not owned) | PAC / ACS |

> **Note:** `status` (persisted) and **derived connectivity** are deliberately
> distinct. EDGE-FE presents the *derived* state; it never trusts a stale `status`
> flag as current truth (ADR-005 local).

## 2. View models (draft)

- **DeviceConnectivityVM** (Shell chip / Home card):
  `{ paired: boolean; derivedState: 'online' | 'offline' | 'stale' | 'unknown';
  lastSyncIso: string | null; lastSyncRelative: string; versao: string | null;
  tone: 'ok' | 'warn' | 'er' }`
- **PairingVM** (pairing flow):
  `{ phase: 'idle' | 'requesting' | 'awaiting-claim' | 'awaiting-first-heartbeat'
  | 'paired' | 'expired' | 'error'; code: string | null; codeExpiresAtIso: string
  | null; deviceId: string | null }`
- **DeviceDetailVM** (device-detail view):
  `{ device: DispositivoEdge; derivedState: DerivedState; lastSyncRelative: string;
  canRePair: boolean; canUnpair: boolean }`

## 3. UI events

- `RequestPairingCode` — conductor starts pairing (onboarding step 3 / detail view).
- `PairingCodeExpired` — code TTL elapsed with no claim.
- `DeviceClaimed` — agent claimed the code (observed via poll).
- `FirstHeartbeatReceived` — first heartbeat arrived → pairing complete.
- `ConnectivityPolled` — a status poll returned (feeds derived state).
- `HeartbeatStale` — derived state crossed the staleness threshold.
- `DeviceWentOffline` — derived state crossed the offline window.
- `RePairRequested` / `UnpairRequested` — replace/remove the device.

## 4. State transitions (UI level)

**Pairing flow:**
```
idle -> requesting -> awaiting-claim -> awaiting-first-heartbeat -> paired
   \                     |                        |
    \--> error <---------+------------------------+
          ^
awaiting-claim -> expired  (code TTL elapsed, no claim)
```

**Derived connectivity (per fresh poll):**
```
        heartbeat age < threshold        threshold <= age < offlineWindow        age >= offlineWindow
online <----------------------------- online/stale --------------------------------> offline
   ^                                        |  (stale)                                   |
   |                                        v                                            |
   +------------------- poll failure -> unknown (degraded) <-----------------------------+
```

## 5. Invariants (client-side)

- **Freshness gate:** `derivedState = 'online'` **only if**
  `now - ultimaSincronizacao < stalenessThreshold` AND the last poll succeeded.
  A raw `status = 'online'` alone is **never** sufficient (ADR-005).
- **No premature paired:** the pairing flow reaches `paired` **only after** a first
  heartbeat, not merely after the code is claimed.
- **Code TTL respected:** an expired pairing code cannot be used; the UI forces a
  re-request rather than hanging in `awaiting-claim`.
- **Single active device:** at most one active `DispositivoEdge` per patient in the
  MVP; re-pairing supersedes the previous bind explicitly.
- **Unknown is not online:** on poll failure, connectivity is `unknown` (degraded),
  never optimistically `online`.
- **Read-only for caregivers:** monitoring views require no privilege; pairing /
  unpair require the conductor entitlement.
