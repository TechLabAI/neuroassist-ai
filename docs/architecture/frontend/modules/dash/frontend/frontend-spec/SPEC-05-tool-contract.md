# SPEC-05 — Module Contract — DASH (`dash.contract.ts`)

> Sources: FE 04 §7, FE 02 §4, ADR-005/006. Content of
> `libs/dash/shell-entry/src/lib/dash.contract.ts` — consumed by the Shell registry.
> DASH is the panel's default landing and the **root** of the sidebar ("Status").

## 1. `MODULE_KEY` and menu metadata

```ts
export const MODULE_KEY = 'dash' as const;

export const DASH_MENU_METADATA: ModuleMenuMetadata = {
  key: MODULE_KEY,
  label: 'Status',            // human-facing pt-BR; root of the sidebar
  icon: 'shield-check',       // calm/health iconography (never an alarm icon — reserved to EVNT)
  order: 0,                   // top of the sidebar; DASH is the default landing
  route: '/',
  showInNav: true,
};
```

## 2. `DASH_PERMISSION_MAP`

```ts
export const DASH_PERMISSION_MAP: ModulePermissionMap = {
  'dash.view':            { grantedTo: ['cuidadora', 'coordenador_clinica'] }, // all authenticated caregivers
  'dash.onboarding.entry':{ grantedTo: ['coordenador_clinica'] },             // condutor/admin only
};
```

**Justification per permission:**

- `dash.view` — every authenticated caregiver may see the panel Home/Status and their own notification
  center. It gates nothing sensitive on its own; the BE re-enforces tenant + caregiver↔patient scope on
  every `/panel/**` call, so this permission is purely a UI-affordance gate.
- `dash.onboarding.entry` — the `Onboarding` sidebar link (an ACS-owned surface DASH only *links to*) is
  shown only to `coordenador_clinica`/condutor técnico. `WHILE` the role is `cuidadora`, the item is hidden.
  DASH authors no onboarding logic — this permission only decides link visibility.

## 3. `DASH_FEATURE_FLAGS`

```ts
export const DASH_FEATURE_FLAGS: ModuleFeatureFlags = {
  'dash.serverSummary':     { defaultState: 'on'  }, // ADR-005 aggregate path (GET /panel/summary)
  'dash.notificationCenter':{ defaultState: 'on'  }, // bell/inbox recent-dispatch list
  'dash.sseStream':         { defaultState: 'on'  }, // live SSE; off ⇒ long-poll fallback
};
```

**Justification per flag:**

- `dash.serverSummary` — when on, Home reads the single server-composed aggregate (ADR-005). Off ⇒ degraded
  shell-composed fallback (kept behind a flag so the aggregate can be dark-launched / rolled back without a
  redeploy).
- `dash.notificationCenter` — gates the `/notificacoes` surface and the topbar bell; lets the center ship
  incrementally without affecting the calm Home.
- `dash.sseStream` — on ⇒ live `/panel/stream`; off ⇒ `/panel/notifications/poll` long-poll. A transport
  kill-switch for environments where SSE is proxied away.

## 4. `DASH_MODULE_DESCRIPTOR` (global outlet declaration)

```ts
export const DASH_MODULE_DESCRIPTOR: ModuleDescriptor = {
  key: MODULE_KEY,
  loadChildren: () => import('@neuroassist/dash').then(m => m.DashModule),
  menu: DASH_MENU_METADATA,
  permissions: DASH_PERMISSION_MAP,
  featureFlags: DASH_FEATURE_FLAGS,
  shellOutletRegions: ['dash.global-interrupt'],   // region EVNT registers alarm/toast INTO (INT-FE-EVNT-001)
};
```

> DASH **declares** the global outlet region; EVNT **registers** its `AlarmOverlayComponent`/toast into it via
> the shared `ShellOutletRegistry`. DASH never imports `scope:evnt` (ADR-005).

## Critérios de aceite (EARS)

- WHILE the caregiver lacks the `dash.onboarding.entry` permission, THE SYSTEM SHALL hide the `Onboarding`
  navigation item.
- THE SYSTEM SHALL grant `dash.view` to every authenticated caregiver and SHALL render the Status Home as
  the default landing.
- WHERE the `dash.serverSummary` flag is off, THE SYSTEM SHALL use the degraded shell-composed summary
  fallback instead of the server aggregate.
- WHERE the `dash.sseStream` flag is off, THE SYSTEM SHALL deliver notifications via the long-poll fallback
  instead of the SSE stream.
- THE SYSTEM SHALL declare the `dash.global-interrupt` shell outlet region in its module descriptor for EVNT
  to register the alarm/toast components.
