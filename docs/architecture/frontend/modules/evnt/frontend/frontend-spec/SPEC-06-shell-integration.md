# SPEC-06 — Shell Integration — EVNT (Frontend)

> Module: **EVNT — Eventos e Alarmes** · Integration lib: `libs/evnt/integration`.
> Traces FE ADR-005 (global alarm outlet + live delivery), ADR-006 shared (module entry as library).
> Acceptance criteria use EARS notation.

## 1. `tool-descriptors.ts` (Shell fallback)

The Shell discovers EVNT through `EVNT_MODULE_DESCRIPTOR` (SPEC-05). When descriptor discovery is
unavailable, a static fallback descriptor keeps the routine feed reachable:

```ts
// apps/neuroassist-ai-web/src/app/tool-descriptors.ts (fallback entry)
export const EVNT_FALLBACK_DESCRIPTOR = {
  key: 'evnt', label: 'Eventos', icon: 'notifications_active',
  route: '/eventos',
  loadChildren: () => import('@neuroassist/evnt').then(m => m.EvntModule),
};
```

## 2. `EvntShellIntegrationService` (`libs/evnt/integration`)

Registers the **global AlarmOutlet** with the Shell and wires the `activeEvent` signal into it. This is
the deliberate non-route surface (ADR-005): the Shell renders `AlarmOverlayComponent` above the
router-outlet, so it pre-empts any active route.

```ts
@Injectable({ providedIn: 'root' })
export class EvntShellIntegrationService {
  private readonly facade = inject(EventDeliveryFacade);
  private readonly shell  = inject(ShellOutletRegistry);  // @neuroassist/shared (scope:web bridge)

  registerAlarmOutlet(): void {
    // Shell renders AlarmOverlayComponent whenever activeEvent is a non-null emergencia.
    this.shell.registerGlobalOutlet('alarm', {
      component: () => import('@neuroassist/evnt/feature-alarm').then(m => m.AlarmOverlayComponent),
      visible: this.facade.alarmVm,     // Signal<AlarmVm> → visible flag
      zLayer: 'interrupt',              // above modals and router-outlet
    });
  }

  unregisterAlarmOutlet(): void { this.shell.unregisterGlobalOutlet('alarm'); }
}
```

Only `scope:web` (the Shell) may depend on every module (`nx-boundaries.md`); EVNT exposes the outlet
via `@neuroassist/evnt` and the Shell consumes the readonly `activeEvent`/`alarmVm` signal — modules
never import each other's feature libs.

## 3. `evnt.bootstrap.ts` (init / destroy)

```ts
export const EVNT_BOOTSTRAP: ModuleBootstrap = {
  async init(ctx) {
    ctx.integration.registerAlarmOutlet();          // (1) register global outlet
    await ctx.facade.hydrate();                     // (2) GET /events/active before stream
    ctx.facade.startLiveChannel();                  // (3) open SSE (or poll if flag/degraded)
    ctx.nav.register(EVNT_NAV_CONFIG);              // (4) routine-feed menu entry
  },
  destroy(ctx) {
    ctx.facade.stopLiveChannel();                   // teardown SSE / polling interval
    ctx.integration.unregisterAlarmOutlet();
  },
};
```

- **init** order is load-bearing: register the outlet, then hydrate, then open the live channel — a
  refresh during an active alarm re-shows the overlay before the first stream frame.
- **destroy** must tear down the SSE connection and any polling interval (no leaked EventSource).

## 4. Navigation / breadcrumb (`libs/evnt/navigation`)

- `EVNT_NAV_CONFIG` registers the "Eventos" menu entry (`/eventos`, order 20).
- Breadcrumbs: `Eventos` → `Eventos › {rótulo} ({hora})` on detail.
- The **AlarmOutlet contributes no breadcrumb and no menu entry** — it is an interrupt, not a location.

## Criterios de aceite (EARS)

- WHEN the EVNT module boots, THE SYSTEM SHALL register the global AlarmOutlet before opening the live
  channel.
- WHEN the EVNT module is destroyed, THE SYSTEM SHALL tear down the live channel and unregister the
  AlarmOutlet so no EventSource or polling interval leaks.
- WHILE `alarmVm.visible` is true, THE SYSTEM SHALL render the AlarmOutlet above the router-outlet and
  above any open modal.
- IF descriptor discovery is unavailable, THEN THE SYSTEM SHALL fall back to the static
  `EVNT_FALLBACK_DESCRIPTOR` so the routine feed stays reachable.
- THE SYSTEM SHALL keep the AlarmOutlet out of the breadcrumb and menu (it changes no URL).
