# SPEC-05 — Tool / Module Contract — EVNT (Frontend)

> Module: **EVNT — Eventos e Alarmes** · File: `libs/evnt/shell-entry/src/lib/evnt.contract.ts`
> Consumed by the Shell via `EVNT_MODULE_DESCRIPTOR`. Traces FE ADR-006 shared (module entry as library).
> Acceptance criteria use EARS notation.

## 1. `evnt.contract.ts` (canonical shape)

```ts
export const MODULE_KEY = 'evnt' as const;

export const EVNT_MENU_METADATA = {
  key: MODULE_KEY,
  label: 'Eventos',                 // pt-BR, human-facing
  icon: 'notifications_active',
  route: '/eventos',
  order: 20,
} as const;

export const EVNT_PERMISSION_MAP = {
  view:        'evnt.view',         // see the routine feed + event detail
  confirm:     'evnt.confirm',      // submit real / falso_positivo verdict
  routineView: 'evnt.routine.view', // see discreet routine notices
} as const;

export const EVNT_FEATURE_FLAGS = {
  liveChannel:   { key: 'evnt.liveChannel',   default: true },   // SSE primary
  pollIntervalMs:{ key: 'evnt.pollIntervalMs', default: 15000 }, // degraded fallback cadence
  routineNotices:{ key: 'evnt.routineNotices', default: true },  // discreet routine tier
} as const;
```

## 2. Permission rationale

| Permission | Grants | Why it exists (RBAC) |
|---|---|---|
| `evnt.view` | Read the routine feed + event detail | Baseline read; a caregiver not linked to the patient must not see events (enforced also by `evntGuard` + BE tenant check). |
| `evnt.confirm` | Submit a verdict (`real`/`falso_positivo`) | The confirmation is an audited, terminal clinical action (BE ADR-006). Coordinators/linked caregivers only; maps to BE scope `evnt:confirm`. |
| `evnt.routine.view` | See discreet routine notices | Some roles receive only emergencies (via the global alarm) and should not be flooded by routine notices; separable from `evnt.view`. |

> The **emergency `AlarmOutlet` is not permission-gated in the UI** — an emergency alarm must be
> impossible to miss for any authenticated caregiver of the tenant (safety invariant). Authorization to
> *confirm* it is still governed by `evnt.confirm` + BE `evnt:confirm`.

## 3. Feature-flag rationale

| Flag | Rationale |
|---|---|
| `liveChannel` | Allows disabling SSE (e.g. hostile proxy) and forcing polling without a redeploy; default on. |
| `pollIntervalMs` | Tunes the degraded-mode latency/load trade-off per deployment. |
| `routineNotices` | Lets a deployment suppress the routine tier entirely (emergency-only mode) for noisy environments. |

## 4. Module descriptor

```ts
export const EVNT_MODULE_DESCRIPTOR = {
  key: MODULE_KEY,
  menu: EVNT_MENU_METADATA,
  permissions: EVNT_PERMISSION_MAP,
  flags: EVNT_FEATURE_FLAGS,
  loadChildren: () => import('@neuroassist/evnt').then(m => m.EvntModule),
  // AlarmOutlet registration hook — Shell renders it above the router-outlet (ADR-005)
  alarmOutlet: () => import('@neuroassist/evnt').then(m => m.EVNT_ALARM_OUTLET),
} as const;
```

## Criterios de aceite (EARS)

- WHILE the caregiver session lacks `evnt.confirm`, THE SYSTEM SHALL disable/hide the confirm control on
  the event detail (the verdict cannot be submitted from the UI).
- WHILE the caregiver session lacks `evnt.routine.view`, THE SYSTEM SHALL hide the routine notices and
  the routine feed entries.
- WHERE the `liveChannel` flag is off, THE SYSTEM SHALL open no SSE stream and SHALL poll `GET /events/active`
  on `pollIntervalMs` instead.
- WHERE the `routineNotices` flag is off, THE SYSTEM SHALL suppress discreet routine notices while still
  delivering emergency alarms.
- THE SYSTEM SHALL render the emergency `AlarmOutlet` for any authenticated caregiver of the tenant
  regardless of `evnt.confirm`, gating only the confirmation action by that permission.
