// @neuroassist/shared/ui-components — Wave-0 stub (D-328/FE-4); flesh out in the implement cycle.
export const UI_COMPONENTS_VERSION = '0.0.0';

// =============================================================================
// ShellOutletRegistry — stub (INT-FE-EVNT-001)
// Global outlet registry that the shell reads to render above-router components.
// The EVNT alarm overlay is the first consumer (ADR-005, SPEC-06 §2).
//
// TODO[INT-FE-EVNT-001]: implement the full registry in scope:web (AppShellComponent).
// The shell must:
//   1. inject ShellOutletRegistry
//   2. read registered outlets reactively (signal<OutletRegistration[]>)
//   3. render each visible outlet in a host element above the router-outlet
// =============================================================================

import { Injectable, Signal, Type } from '@angular/core';

export type OutletZLayer = 'interrupt' | 'modal' | 'toast';

export interface OutletRegistration {
  readonly key: string;
  readonly component: () => Promise<Type<unknown>>;
  readonly visible: Signal<{ visible: boolean }>;
  readonly zLayer: OutletZLayer;
}

@Injectable({ providedIn: 'root' })
export class ShellOutletRegistry {
  private readonly _outlets = new Map<string, OutletRegistration>();

  registerGlobalOutlet(key: string, registration: Omit<OutletRegistration, 'key'>): void {
    this._outlets.set(key, { key, ...registration });
    // TODO[INT-FE-EVNT-001]: notify AppShellComponent to render this outlet
  }

  unregisterGlobalOutlet(key: string): void {
    this._outlets.delete(key);
    // TODO[INT-FE-EVNT-001]: notify AppShellComponent to remove this outlet
  }

  getOutlets(): ReadonlyMap<string, OutletRegistration> {
    return this._outlets;
  }
}
