# SPEC-06 — Shell Integration — EDGE (Frontend)

> How the `scope:web` shell discovers, mounts and composes EDGE. EDGE integrates as (a) an **always-on
> connectivity read-model** the topbar/Home consume, (b) a **composed pairing surface** for onboarding
> step 3, and (c) a lazy thin host for dev/e2e and the chip target.

## 1. `EDGE_MODULE_DESCRIPTOR` (barrel export)

Exported from `@neuroassist/edge` (`edge.descriptor.ts`). The shell reads it to register the module without
importing internals (nx-boundaries, ADR-006).

```ts
export const EDGE_MODULE_DESCRIPTOR = {
  key: 'edge',
  entry: () => import('@neuroassist/edge').then(m => m.EdgeModule),
  routes: EDGE_ROUTES,                 // thin-host + chip target (SPEC-01)
  permissionMap: MODULE_PERMISSION_MAP,
  featureFlags: MODULE_FEATURE_FLAGS,
  menu: MODULE_MENU_METADATA,          // showInNav:false
  bootstrap: edgeBootstrap,
  // composition slot A: the always-visible connectivity read-model the topbar/Home read
  connectivityReadModel: (deps: EdgeReadModelDeps) => Signal<DeviceConnectivityVM>,
  // composition slot B: the pairing surface the onboarding step-3 host page renders
  pairingComponent: () => import('@neuroassist/edge').then(m => m.DevicePairingPageComponent),
} as const;
```

## 2. `tool-descriptors.ts` (Shell fallback)

The shell keeps a static fallback descriptor so a build works even if dynamic discovery is unavailable. For
EDGE the fallback mirrors §1 with `showInNav:false` (no menu contribution). Because EDGE is composed, the
important fields for the shell are `connectivityReadModel` (topbar) and `pairingComponent` (onboarding) —
resolved through the shell, never through a direct `scope:edge` import from other modules.

## 3. `EdgeShellIntegrationService` (`libs/edge/integration/`)

```ts
class EdgeShellIntegrationService {
  registerWithShell(): void;                             // publish descriptor + composition slots
  connectivityReadModel(): Signal<DeviceConnectivityVM>; // readonly, derived from DeviceFacade (single source, ADR-005)
  startConnectivityForActivePatient(): void;             // resolve device + begin the poll timer (paused when hidden)
  stopConnectivity(): void;                              // teardown the poll timer
  resolvePairingSlot(): Promise<Type<DevicePairingPageComponent>>; // onboarding step-3 host page
}
```

- `connectivityReadModel()` is the **only sanctioned path** for the topbar/Home (at `scope:web`) to read
  connectivity, preserving the nx-boundary (`scope:web` may compose `scope:edge`; the reverse and sibling
  imports never happen — doc 02 §4). It is a readonly signal derived from `DeviceFacade` — no consumer
  re-derives `online` from a raw flag.
- `resolvePairingSlot()` gives the onboarding host page (`scope:web`) `DevicePairingPageComponent` without
  `scope:acs`/onboarding importing `scope:edge` internals.
- Contributes the `Dispositivo` breadcrumb only under EDGE's own routes; contributes **no** breadcrumb when
  composed (host owns the trail).

## 4. `edge.bootstrap.ts` — init / destroy

```ts
export const edgeBootstrap = {
  init(): void {
    // resolve the active patient's device (GET /patients/{pacienteId}/device) and START connectivity
    // polling so the topbar chip is honest from first paint; register descriptor + slots with the shell.
    // Does NOT open any pairing flow — pairing is an explicit conductor action.
  },
  destroy(): void {
    // stop the connectivity poll timer (no leaked interval/timer on module unload); release the read-model.
  },
};
```

- `init` starts the poll **paused-when-hidden** (Page Visibility), so an authenticated but backgrounded tab
  does not hammer the gateway (doc 06 R2).
- `destroy` is the last-line guarantee that no poll timer leaks when the module unloads (reinforces the
  facade's `stopPolling`).

## 5. Cross-module composition (authoritative)

```
scope:web topbar        ──connectivityReadModel()──▶ EdgeShellIntegrationService
        │                                                   │
        │◀────────────── Signal<DeviceConnectivityVM> ──────┘
        ▼
   renders <edge-connectivity-chip [derivedState]=… [tone]=… (open)="/edge/device"/>

scope:web onboarding    ──resolvePairingSlot()──▶ EdgeShellIntegrationService
        │                                                   │
        │◀──────────── Type<DevicePairingPageComponent> ────┘
        ▼
   renders <edge-device-pairing [pacienteHint]=… (paired)=…/>
```

The prototype's `topbar.component.ts` reads `DispositivosFacade.items()[0].status` directly (the raw flag)
and `onboarding.component.ts` fakes pairing with a `setTimeout`. Both are replaced by this composition:
the topbar binds the derived read-model; onboarding hosts the real pairing surface (ADR-005 / doc 02 §5).
PAC/ACS/onboarding never touch `scope:edge`; the shell page is the composition point.

## Critérios de aceite (EARS)

- THE SYSTEM SHALL expose `EDGE_MODULE_DESCRIPTOR` through the `@neuroassist/edge` barrel for shell registration.
- WHEN the shell topbar requests connectivity, THE SYSTEM SHALL provide the derived `DeviceConnectivityVM` read-model through `EdgeShellIntegrationService` without requiring a direct `scope:edge` import from the shell internals.
- WHEN a `scope:web` onboarding host page requests the pairing slot, THE SYSTEM SHALL resolve `DevicePairingPageComponent` through `EdgeShellIntegrationService` without a direct `scope:edge` import from onboarding.
- WHEN the module bootstrap `init` runs, THE SYSTEM SHALL resolve the active patient's device and start connectivity polling paused while the tab is hidden.
- WHEN the module bootstrap `destroy` runs, THE SYSTEM SHALL stop the connectivity poll timer so no interval leaks.
