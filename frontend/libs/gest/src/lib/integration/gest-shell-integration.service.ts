import { Injectable, Type } from '@angular/core';
import { GEST_MODULE_DESCRIPTOR } from '../gest.descriptor';

// ---------------------------------------------------------------------------
// GestShellIntegrationService — GEST-INT-001
// The ONLY sanctioned way for scope:web host pages (PAC calibracao page,
// onboarding gesto step) to obtain GestureLiveTestComponent. scope:pac and
// scope:acs must never import @neuroassist/gest directly (GEST-INT-002
// nx-boundary) — they expose an ng-content slot; the host page injects this
// service and projects the resolved component into it.
// ---------------------------------------------------------------------------

@Injectable({ providedIn: 'root' })
export class GestShellIntegrationService {
  private registered = false;

  /** Called by gestBootstrap.init() when the GEST module is first loaded. */
  registerWithShell(): void {
    this.registered = true;
  }

  get isRegistered(): boolean {
    return this.registered;
  }

  /** Resolves the live-test component for content-projection into a host
   * page's [slot=live-test] outlet (e.g. pac-command-editor). */
  resolveLiveTestSlot(): Promise<Type<unknown>> {
    return GEST_MODULE_DESCRIPTOR.liveTestComponent();
  }
}
