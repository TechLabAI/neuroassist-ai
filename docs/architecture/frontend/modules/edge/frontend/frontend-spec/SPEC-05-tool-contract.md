# SPEC-05 — Module Contract — EDGE (`edge.contract.ts`)

> Lives in `libs/edge/shell-entry/src/lib/edge.contract.ts`. Consumed by the `scope:web` shell for
> descriptor fallback, permission gating and feature-flag resolution. EDGE is a **composed** module —
> its primary surface is the always-visible topbar chip + onboarding pairing; it is deliberately **not**
> a top-level nav item.

## 1. `MODULE_KEY`

```ts
export const MODULE_KEY = 'edge' as const;
```

## 2. `MODULE_MENU_METADATA`

```ts
export const MODULE_MENU_METADATA = {
  key: MODULE_KEY,
  label: 'Dispositivo',              // pt-BR (human-facing)
  icon: 'wifi',                       // topbar chip reuses the connectivity icon (prototype topbar.component.ts)
  showInNav: false,                   // composed surface: topbar chip + onboarding step 3; no dead menu entry
  order: null,
} as const;
```

Rationale: EDGE has no standalone product route in the nav (SPEC-01). Connectivity is reached through the
always-visible chip (which links to `/edge/device`); pairing is reached through onboarding step 3.
`showInNav: false` prevents a dead top-level menu entry.

## 3. `MODULE_PERMISSION_MAP`

```ts
export const MODULE_PERMISSION_MAP = {
  'edge.device.read':   ['CUIDADORA_FAMILIAR', 'CONDUCTOR'],   // view connectivity + device detail
  'edge.device.manage': ['CONDUCTOR'],                         // register / pair / unpair / retire
} as const;
```

| Permission | BE scope (api-contract §6.2) | Justification |
|---|---|---|
| `edge.device.read` | `edge.read` | Any authenticated tenant member sees the connectivity chip and the device-detail view. Monitoring is safety-critical and non-destructive, so the family caregiver (`CUIDADORA_FAMILIAR`) always has it — she must never be blind to a dropped device (doc 01 §3). |
| `edge.device.manage` | `edge.manage` | Register a device, pair it to a patient, unpair, and retire are **conductor-only** onboarding/operations actions (Danilo). A caregiver never pairs or replaces hardware. Maps 1:1 to the single BE `edge.manage` scope, so the FE need not model per-verb scopes. |

## 4. `MODULE_FEATURE_FLAGS`

```ts
export const MODULE_FEATURE_FLAGS = {
  pollingEnabled:       { default: true },     // derived-connectivity polling cadence
  pollIntervalMs:       { default: 15000 },    // matches BE liveness.poll-hint-seconds (api-contract §5)
  stalenessThresholdMs: { default: 45000 },    // FE 'stale' band entry (earlier than BE 90s offline)
  offlineWindowMs:      { default: 90000 },    // aligns with BE stalenessThresholdSeconds (api-contract §5)
  rePairEnabled:        { default: true },     // exposes re-pair/unpair/retire actions in device detail
} as const;
```

| Flag | Default | Justification |
|---|---|---|
| `pollingEnabled` | `on` | Master switch for the connectivity poll (ADR-005). Off ⇒ a single fetch with no cadence — used in demos/tests; the chip then shows the last snapshot and cannot claim continued freshness. |
| `pollIntervalMs` | `15000` | Advisory DASH poll hint from the BE (api-contract §5). Tunable per pilot to trade freshness vs. gateway load (doc 06 R2). |
| `stalenessThresholdMs` | `45000` | The **safety-tuning** parameter (doc 06 R1): the age at which the chip degrades to `stale`. Deliberately shorter than the BE 90 s `offline` so caregivers get an early, non-alarming warning (ADR-005 rejected binary-only). |
| `offlineWindowMs` | `90000` | The age at which the chip goes `offline` (loud). Defaulted to the BE window so FE and BE agree on `offline`. |
| `rePairEnabled` | `on` | Gates the device-management actions. Off hides re-pair/unpair/retire (e.g. a locked-down pilot) without removing read access. |

The thresholds are tunable **without redeploy** — they are live safety parameters, not code constants
(ADR-005). They never soften the honesty invariant: `unknown` and `stale` are never rendered as `online`.

## 5. RBAC of UI (behavioral)

- **Read surfaces** (connectivity chip, device-detail card/meta) are visible to everyone with
  `edge.device.read` — i.e. every authenticated tenant member.
- **Management actions** (re-pair, unpair, retire; the `/edge/pairing` route) are gated on
  `edge.device.manage`. When the session lacks it, the `DeviceActionsComponent` is **hidden** (not merely
  disabled) so a caregiver is never shown an affordance she cannot use, and `edgeConductorGuard` redirects
  the pairing route to read-only detail (SPEC-01).

## Critérios de aceite (EARS)

- THE SYSTEM SHALL expose `MODULE_KEY = 'edge'` and SHALL register the module with `showInNav: false`.
- WHILE the current user lacks the `edge.device.manage` permission, THE SYSTEM SHALL hide the re-pair, unpair, and retire controls.
- WHILE the current user lacks the `edge.device.manage` permission, THE SYSTEM SHALL prevent activation of the `/edge/pairing` route.
- WHERE the `rePairEnabled` feature flag is off, THE SYSTEM SHALL hide the device-management actions while keeping the connectivity read surface visible.
- WHERE the `pollingEnabled` feature flag is off, THE SYSTEM SHALL fetch the device once and SHALL NOT assert continued freshness on the connectivity chip.
- THE SYSTEM SHALL treat the staleness and offline thresholds as runtime feature flags and SHALL NOT render `stale` or `unknown` as `online`.
