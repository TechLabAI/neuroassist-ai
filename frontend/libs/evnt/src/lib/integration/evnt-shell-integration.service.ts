import { Injectable, inject } from '@angular/core';
import { ShellOutletRegistry } from '@neuroassist/shared/ui-components';
import { EventDeliveryFacade } from '../evnt.facade';

// =============================================================================
// EvntShellIntegrationService — SPEC-06 §2 (ADR-005 global alarm outlet)
// Registers the AlarmOverlayComponent with the ShellOutletRegistry so the shell
// renders it above the router-outlet (z-layer: interrupt) whenever alarmVm.visible
// is true — pre-empting whatever screen is active.
//
// This is the deliberate non-route surface for emergency alarms.
// Called from evnt.bootstrap.ts init/destroy.
// =============================================================================

@Injectable({ providedIn: 'root' })
export class EvntShellIntegrationService {
  private readonly facade = inject(EventDeliveryFacade);
  private readonly shellRegistry = inject(ShellOutletRegistry);
  private registered = false;

  /** Register the global AlarmOutlet with the shell (SPEC-06 §3 step 1).
   * Must be called BEFORE hydrate() and startLiveChannel(). */
  registerAlarmOutlet(): void {
    if (this.registered) return;
    this.registered = true;
    this.shellRegistry.registerGlobalOutlet('alarm', {
      component: () =>
        import('../feature-alarm/alarm-overlay.component').then(
          (m) => m.AlarmOverlayComponent,
        ),
      visible: this.facade.alarmVm,  // Signal<AlarmVm> — shell reads .visible
      zLayer: 'interrupt',           // above modals and router-outlet
    });
  }

  /** Unregister the AlarmOutlet on module destroy (LIFECYCLE-01, SPEC-06 §3 destroy). */
  unregisterAlarmOutlet(): void {
    this.shellRegistry.unregisterGlobalOutlet('alarm');
    this.registered = false;
  }

  isRegistered(): boolean {
    return this.registered;
  }
}
