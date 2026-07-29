import { Injectable, Type } from '@angular/core';
import { GestureLiveTestComponent } from '../feature-live-test/gesture-live-test.component';

// ---------------------------------------------------------------------------
// GestShellIntegrationService — SPEC-06 §3, GEST-INT-001
// The ONLY sanctioned path for scope:web host pages (PAC calibracao, onboarding)
// to obtain GestureLiveTestComponent.
//
// nx-boundary rule (GEST-INT-002): scope:pac and scope:acs MUST NOT import
// scope:gest directly. They use this service (provided at scope:web level) to
// resolve the component type, preserving the boundary.
//
// Composition pattern (SPEC-06 §5):
//   scope:web host page
//     → resolveLiveTestSlot()
//       → GestShellIntegrationService
//         → Type<GestureLiveTestComponent>
//     → renders <gest-live-test [commandDraft]=... (testCompleted)=.../>
// ---------------------------------------------------------------------------

/** Registry entry contributed by GEST to the shell. */
export interface GestModuleRegistration {
  readonly key: string;
  readonly showInNav: false; // GEST never appears in sidebar navigation
  readonly liveTestComponent: Type<GestureLiveTestComponent>;
}

@Injectable({ providedIn: 'root' })
export class GestShellIntegrationService {
  private registered = false;
  private readonly registration: GestModuleRegistration = {
    key: 'gest',
    showInNav: false,
    liveTestComponent: GestureLiveTestComponent,
  };

  /** Register the GEST module descriptor with the shell registry.
   * Called from gestBootstrap.init() (SPEC-06 §4). */
  registerWithShell(): void {
    if (this.registered) return;
    this.registered = true;
    // TODO[GEST-INT-001]: wire to shell module registry when the registry service
    // is implemented (scope:web shell bootstrap). For now, this service is self-contained.
  }

  /** Returns the GestureLiveTestComponent type so scope:web host pages can render it
   * without importing scope:gest directly (preserves nx-boundary, GEST-INT-002). */
  resolveLiveTestSlot(): Promise<Type<GestureLiveTestComponent>> {
    // Sync in the current implementation — async signature preserved for forward
    // compatibility with remote-entry dynamic loading (post-MVP).
    return Promise.resolve(this.registration.liveTestComponent);
  }

  /** Check whether GEST has been registered with the shell. */
  isRegistered(): boolean {
    return this.registered;
  }
}
