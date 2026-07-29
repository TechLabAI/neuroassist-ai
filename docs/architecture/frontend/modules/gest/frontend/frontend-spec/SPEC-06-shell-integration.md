# SPEC-06 — Shell Integration — GEST (Frontend)

> How the `scope:web` shell discovers, mounts and composes GEST. GEST integrates
> as an **embedded feature** (no nav route) plus a lazy thin host for dev/e2e.

## 1. `GEST_MODULE_DESCRIPTOR` (barrel export)

Exported from `@neuroassist/gest` (`gest.descriptor.ts`). The shell reads it to
register the module without importing internals.

```ts
export const GEST_MODULE_DESCRIPTOR = {
  key: 'gest',
  entry: () => import('@neuroassist/gest').then(m => m.GestModule),
  routes: GEST_ROUTES,                 // thin-host only (SPEC-01)
  permissionMap: MODULE_PERMISSION_MAP,
  featureFlags: MODULE_FEATURE_FLAGS,
  menu: MODULE_MENU_METADATA,          // showInNav:false
  bootstrap: gestBootstrap,
  // composition slot: the shell renders GestureLiveTestComponent inside host pages
  liveTestComponent: () => import('@neuroassist/gest').then(m => m.GestureLiveTestComponent),
} as const;
```

## 2. `tool-descriptors.ts` (Shell fallback)

The shell keeps a static fallback descriptor so a build works even if dynamic
discovery is unavailable. For GEST the fallback entry mirrors §1, with
`showInNav:false` (no menu contribution). Because GEST is composed, the important
field for the shell is `liveTestComponent` — the host pages resolve it through the
shell, never through a direct `scope:gest` import.

## 3. `GestShellIntegrationService` (`libs/gest/integration/`)

```ts
class GestShellIntegrationService {
  registerWithShell(): void;   // publish descriptor + composition slot to the shell registry
  resolveLiveTestSlot(): Promise<Type<GestureLiveTestComponent>>;  // used by scope:web host pages
}
```

- Provides the **only sanctioned path** for PAC/onboarding host pages (at
  `scope:web`) to obtain `GestureLiveTestComponent`, preserving the nx-boundary
  (`scope:pac`/`scope:acs` must not import `scope:gest` — doc 02 §4, R6 doc 06).
- Contributes no breadcrumb when composed (host owns it); contributes the single
  `Calibração › Teste ao vivo` trail only under the thin host `apps/gest`.

## 4. `gest.bootstrap.ts` — init / destroy

```ts
export const gestBootstrap = {
  init(): void {
    // negotiate detection-contract version (GET /api/v1/gest/detection-contract),
    // memoize contractVersion for the session; prepare edge bridge token provider.
    // Does NOT open the camera or the stream — those are per-test, on explicit action.
  },
  destroy(): void {
    // ensure any live GestureTestFacade has stopped its MediaStream + closed the bridge;
    // release the memoized contract descriptor.
  },
};
```

- `init` is lifecycle-safe: it never acquires the camera on load (privacy-by-default,
  doc 01 §6 / doc 03 §5).
- `destroy` is the last-line guarantee that no `MediaStream` leaks when the module
  unloads (reinforces the facade's per-test teardown).

## 5. Cross-module composition (authoritative)

```
scope:web host page  ──resolveLiveTestSlot()──▶ GestShellIntegrationService
        │                                              │
        │◀──────────── Type<GestureLiveTestComponent> ─┘
        ▼
  renders <gest-live-test [commandDraft]=… (testCompleted)=…/>
```

PAC/ACS never touch `scope:gest`. The shell page is the composition point.

## Critérios de aceite (EARS)

- THE SYSTEM SHALL expose `GEST_MODULE_DESCRIPTOR` through the `@neuroassist/gest` barrel for shell registration.
- WHEN a `scope:web` host page requests the live-test slot, THE SYSTEM SHALL resolve `GestureLiveTestComponent` through `GestShellIntegrationService` without requiring a direct `scope:gest` import from PAC or ACS.
- WHEN the module bootstrap `init` runs, THE SYSTEM SHALL negotiate the detection-contract version and SHALL NOT open the camera.
- WHEN the module bootstrap `destroy` runs, THE SYSTEM SHALL ensure no `MediaStream` track remains active.
