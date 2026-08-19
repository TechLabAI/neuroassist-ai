import { Injectable, Signal, inject, signal } from '@angular/core';
import { ShellOutletRegistry } from '@neuroassist/shared/ui-components';
import { DASH_MODULE_DESCRIPTOR } from '../dash.descriptor';
import { DASH_NAV_CONFIG } from '../dash-nav-config';
import { DASH_ACTIVE_EVENT_TOKEN, DASH_DEVICE_BADGE_TOKEN } from '../access/dash-access-tokens';
import type { ActiveEventVm, DeviceBadgeVm } from '../access/dash-access-tokens';

// =============================================================================
// DashShellIntegrationService — SPEC-06 §2 (Shell Integration)
//
// Bridges DASH into the shell without scope:dash reaching into scope:evnt or scope:edge.
// Consumed by scope:web shell to:
//   1. Register DASH module descriptor + nav config (idempotent)
//   2. Declare the `dash.global-interrupt` outlet region in ShellOutletRegistry
//   3. Accept cross-module signals injected at scope:web (activeEvent, deviceBadge)
//   4. Release the outlet region on destroy
//
// nx-boundary rule: scope:web wires the signals; scope:dash NEVER imports scope:evnt
// or scope:edge directly (ADR-005, INT-FE-DASH-001/002).
// =============================================================================

// ---------------------------------------------------------------------------
// Minimal shell contracts — stubs until scope:web promotes ShellRegistry
// TODO[INT-FE-DASH-003]: replace with real ShellRegistry from scope:web when available.
// ---------------------------------------------------------------------------

/** Breadcrumb segment pushed to the shell breadcrumb trail. */
export interface DashBreadcrumbItem {
  label: string;
  route?: string;
}

/** Minimal push-only breadcrumb contract that the shell BreadcrumbService must satisfy. */
export interface DashBreadcrumbPort {
  push(items: DashBreadcrumbItem[]): void;
}

/** Minimal shell module registry contract.
 * TODO[INT-FE-DASH-003]: replace with real ShellRegistry from scope:web when available. */
export interface DashShellRegistry {
  /** Register the DASH module descriptor (routes, nav config, permission map). */
  registerModule(descriptor: typeof DASH_MODULE_DESCRIPTOR): void;
  /** Register DASH navigation config (sidebar entry, order: 0). */
  registerNavConfig(config: typeof DASH_NAV_CONFIG): void;
}

/** Outlet region key owned by DASH for EVNT alarm overlay mounting. */
const DASH_GLOBAL_INTERRUPT_REGION = 'dash.global-interrupt';

@Injectable({ providedIn: 'root' })
export class DashShellIntegrationService {
  // ShellOutletRegistry is available from @neuroassist/shared/ui-components (stub — INT-FE-EVNT-001).
  // TODO[INT-FE-DASH-003]: ensure ShellOutletRegistry is fully stabilized before wiring EVNT content.
  private readonly outletRegistry = inject(ShellOutletRegistry);

  /** Guard against duplicate registrations (idempotent). */
  private _registered = false;

  // ---------------------------------------------------------------------------
  // registerWithShell — idempotent (safe to call multiple times)
  // ---------------------------------------------------------------------------

  /**
   * Registers DASH nav item ("Status", order: 0) and declares the
   * `dash.global-interrupt` outlet region with ShellOutletRegistry.
   *
   * Idempotent — guarded by `_registered`; safe to call on HMR, route revisits, etc.
   *
   * @param shell - Shell module registry. When scope:web ShellRegistry is not yet
   *   available, pass a no-op stub; the declaration is still recorded internally.
   *   TODO[INT-FE-DASH-003]: replace DashShellRegistry with real ShellRegistry from scope:web.
   */
  registerWithShell(shell: DashShellRegistry): void {
    if (this._registered) return;
    this._registered = true;

    // Contribute DASH module to shell registry (routes, nav, permission map).
    shell.registerModule(DASH_MODULE_DESCRIPTOR);
    shell.registerNavConfig(DASH_NAV_CONFIG);

    // Declare the global-interrupt outlet region so EVNT can mount its alarm overlay.
    // DASH provides the HOST region; EVNT provides the CONTENT (ADR-005, SPEC-06 §2).
    // TODO[INT-FE-DASH-003]: replace stub registration with a full OutletRegistration
    //   (real component + reactive visible signal) when ShellOutletRegistry is stabilized
    //   and EVNT AlarmOverlayComponent is scaffolded.
    class DashInterruptPlaceholder {}
    this.outletRegistry.registerGlobalOutlet(DASH_GLOBAL_INTERRUPT_REGION, {
      // Placeholder component — EVNT will provide the real AlarmOverlayComponent.
      component: () => Promise.resolve(DashInterruptPlaceholder as unknown as import('@angular/core').Type<unknown>),
      // Placeholder visibility signal — always hidden until EVNT connects.
      visible: signal({ visible: false }),
      zLayer: 'interrupt',
    });
  }

  // ---------------------------------------------------------------------------
  // bindActiveEvent — cross-module signal injection (INT-FE-DASH-001)
  // ---------------------------------------------------------------------------

  /**
   * Accepts the EVNT `activeEvent` signal injected at scope:web.
   *
   * The signal is stored via the InjectionToken mechanism — scope:dash reads it
   * from `DASH_ACTIVE_EVENT_TOKEN`, never importing scope:evnt directly.
   *
   * In practice, scope:web calls:
   *   `provide(DASH_ACTIVE_EVENT_TOKEN, { useValue: evntFacade.activeEvent })`
   * and this method is a documentation + wiring confirmation hook.
   *
   * TODO[INT-FE-DASH-001]: override DASH_ACTIVE_EVENT_TOKEN in app.config.ts when
   *   EVNT FE exports its activeEvent signal.
   */
  bindActiveEvent(source: Signal<ActiveEventVm | null>): void {
    // The real binding happens at DI level (InjectionToken factory in dash-access-tokens.ts).
    // This method serves as the documented shell seam hook (SPEC-06 §2, ADR-005).
    // scope:web calls this to confirm the wiring is established for observability/logging.
    void source; // type-safe acknowledgment; no runtime side effect needed
  }

  // ---------------------------------------------------------------------------
  // bindDeviceBadge — cross-module signal injection (INT-FE-DASH-002)
  // ---------------------------------------------------------------------------

  /**
   * Accepts the EDGE `deviceBadge` signal injected at scope:web.
   *
   * TODO[INT-FE-DASH-002]: override DASH_DEVICE_BADGE_TOKEN in app.config.ts when
   *   EDGE FE exports its deviceBadge signal.
   */
  bindDeviceBadge(source: Signal<DeviceBadgeVm | null>): void {
    // Symmetric to bindActiveEvent. Real binding via DASH_DEVICE_BADGE_TOKEN DI token.
    void source; // type-safe acknowledgment
  }

  // ---------------------------------------------------------------------------
  // releaseOutletRegion — on destroy
  // ---------------------------------------------------------------------------

  /**
   * Releases the `dash.global-interrupt` outlet region from ShellOutletRegistry.
   * Called by dashDestroy() when the DASH module is torn down (e.g., lazy unload).
   *
   * Safe to call multiple times (ShellOutletRegistry.unregisterGlobalOutlet is
   * a Map.delete call — no-op if key is already absent).
   */
  releaseOutletRegion(): void {
    this.outletRegistry.unregisterGlobalOutlet(DASH_GLOBAL_INTERRUPT_REGION);
    this._registered = false;
  }

  // ---------------------------------------------------------------------------
  // isRegistered — observable state (testing / shell health checks)
  // ---------------------------------------------------------------------------

  /** Whether DASH has been registered with the shell registry. */
  isRegistered(): boolean {
    return this._registered;
  }
}

// Re-export token references so scope:web can type the injection correctly.
export { DASH_ACTIVE_EVENT_TOKEN, DASH_DEVICE_BADGE_TOKEN };
export type { ActiveEventVm, DeviceBadgeVm };
