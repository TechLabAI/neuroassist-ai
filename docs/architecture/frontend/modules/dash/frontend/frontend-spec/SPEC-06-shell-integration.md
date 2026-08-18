# SPEC-06 — Shell Integration — DASH (Frontend)

> Sources: FE 02 §4/§5, FE 04 §2/§7, ADR-005/006, INT-FE-EVNT-001. DASH is the composition **root** of the
> panel; it owns the shell chrome design + contracts, hosts EVNT's global outlet, and is the default landing.

## 1. `dash-descriptor.ts` (Shell fallback registration)

`libs/dash/shell-entry/src/lib/dash.descriptor.ts` exports `DASH_MODULE_DESCRIPTOR` (SPEC-05 §4). The shell
(`neuroassist-ai-web`) registers it in `config/module-descriptors.ts`. The descriptor declares:

- `loadChildren` → `@neuroassist/dash` (`DashModule`, ADR-006);
- `menu` → "Status" root nav item, order 0;
- `shellOutletRegions: ['dash.global-interrupt']` → the region into which EVNT mounts alarm/toast.

If the runtime descriptor registry is unavailable, the shell uses this static descriptor as the fallback so
DASH still mounts as the default route.

## 2. `DashShellIntegrationService` (`libs/dash/integration`, `type:domain`)

Responsibilities:

- **Register** DASH's nav + outlet region with the shell on bootstrap (idempotent).
- **Expose the cross-module seam** (ADR-005): accept `activeEvent` (EVNT) and `device` (EDGE) values that
  the shell (`scope:web`) resolves and injects — via `input()`/injection tokens — into DASH facades.
  `scope:dash` never imports `scope:evnt`/`scope:edge`; the wiring is a shell concern.
- **Declare** the global outlet region and hand it to the shared `ShellOutletRegistry`
  (`@neuroassist/shared/ui-components`) so EVNT can register its `AlarmOverlayComponent`/toast
  (INT-FE-EVNT-001). DASH provides the **host**; EVNT provides the **content**.

```ts
@Injectable({ providedIn: 'root' })
export class DashShellIntegrationService {
  registerWithShell(shell: ShellRegistry): void;                 // menu + outlet region (idempotent)
  bindActiveEvent(source: Signal<ActiveEventVm | null>): void;   // EVNT signal injected at scope:web
  bindDeviceBadge(source: Signal<DeviceBadgeVm>): void;          // EDGE signal injected at scope:web
  releaseOutletRegion(): void;                                   // on destroy
}
```

## 3. `dash.bootstrap.ts` — init / destroy

`libs/dash/shell-entry/src/lib/dash.bootstrap.ts`:

- **init:** register the descriptor + nav; declare the `dash.global-interrupt` outlet region with
  `ShellOutletRegistry`; open the `/panel/stream` SSE session via `NotificationsFacade` once a caregiver
  session exists; prime the Home summary on first Home entry.
- **destroy:** close the SSE `EventSource` (BE evicts the `PanelSession`); release the outlet region so EVNT's
  registration is torn down cleanly; clear DASH facade signals.

## 4. Composition boundaries (nx)

`scope:dash` imports only `scope:dash` + `scope:shared`. The topbar device badge (EDGE) and the calm-truth
`activeEvent` (EVNT) are composed at `scope:web` and injected; the alarm overlay content is EVNT's, mounted
into DASH's declared region. This keeps `@nx/enforce-module-boundaries` green (nx-boundaries.md, ADR-005).

## Critérios de aceite (EARS)

- WHEN DASH bootstraps, THE SYSTEM SHALL register its `Status` nav item and declare the
  `dash.global-interrupt` outlet region with the shared `ShellOutletRegistry`.
- WHEN a caregiver session is established, THE SYSTEM SHALL open the `/panel/stream` SSE session on bootstrap.
- WHEN the DASH module is destroyed, THE SYSTEM SHALL close the SSE session and release its outlet region.
- WHILE running inside the shell, THE SYSTEM SHALL consume the `activeEvent` (EVNT) and `device` (EDGE)
  signals injected at `scope:web` and SHALL NOT import another module's library directly.
- IF the runtime descriptor registry is unavailable, THEN THE SYSTEM SHALL mount DASH from the static
  `DASH_MODULE_DESCRIPTOR` fallback.
