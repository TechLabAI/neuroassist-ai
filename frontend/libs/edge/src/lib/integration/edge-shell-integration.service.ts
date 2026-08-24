import { Injectable, inject, Signal, Type } from '@angular/core';
import { DeviceFacade } from '../edge.facade';
import { EdgeActivePatientService } from './edge-active-patient.service';
import { PairingFacade } from '../edge-pairing.facade';
import { DeviceConnectivityVM, PairingPhase } from '@neuroassist/shared/contracts';
import { DevicePairingPageComponent } from '../feature-pairing/device-pairing-page.component';

// ---------------------------------------------------------------------------
// EdgeShellIntegrationService — SPEC-06 §3
// The ONLY sanctioned path for scope:web (topbar, Home, onboarding) to
// access EDGE read-models and composition components.
//
// nx-boundary rule: scope:web MUST NOT import scope:edge internals directly.
// The topbar reads connectivity through connectivityReadModel();
// onboarding step-3 obtains the pairing component through resolvePairingSlot()
// and tracks pairing progress through pairingPhase().
// ---------------------------------------------------------------------------

@Injectable({ providedIn: 'root' })
export class EdgeShellIntegrationService {
  private readonly deviceFacade = inject(DeviceFacade);
  private readonly pairingFacade = inject(PairingFacade);
  private registered = false;

  private readonly pacienteAtivoStore = inject(EdgeActivePatientService);

  /** Register the EDGE module descriptor with the shell registry.
   * Called from edgeBootstrap.init() (SPEC-06 §4). Idempotent. */
  registerWithShell(): void {
    if (this.registered) return;
    this.registered = true;
    // TODO[EDGE-INT-SHELL]: wire to shell module registry when implemented
  }

  /**
   * Returns the derived DeviceConnectivityVM as a readonly Signal.
   * This is the ONLY sanctioned way for the topbar / Home to read connectivity.
   * Preserves nx-boundary: scope:web ← EdgeShellIntegrationService ← scope:edge.
   *
   * SPEC-06 §3: "connectivityReadModel() is the only sanctioned path for the topbar/Home"
   */
  connectivityReadModel(): Signal<DeviceConnectivityVM> {
    return this.deviceFacade.connectivity;
  }

  /**
   * Returns the current pairing phase as a readonly Signal.
   * Host pages (onboarding step-3) use this to track pairing progress
   * without importing PairingFacade directly (preserves nx-boundary).
   * SPEC-06 §3: "pairingPhase() exposes PairingFacade.phase to host pages"
   */
  pairingPhase(): Signal<PairingPhase> {
    return this.pairingFacade.phase;
  }

  /**
   * Start connectivity polling for the active patient.
   * Called at shell bootstrap after the session is authenticated.
   * @param pacienteId - active patient id from empresa-context
   */
  startConnectivityForActivePatient(pacienteId: string): void {
    // `ambiente: true` — ninguém pediu esta chamada; ela alimenta o chip. Ver
    // DeviceFacade.resolveForActivePatient.
    this.pacienteAtivoStore.definir(pacienteId);
    this.deviceFacade.resolveForActivePatient(pacienteId, true);
    this.deviceFacade.startPolling();
  }

  /** Stop the connectivity poll timer (called on module unload / logout). */
  stopConnectivity(): void {
    this.deviceFacade.stopPolling();
  }

  /**
   * Returns DevicePairingPageComponent so the onboarding step-3 host page can
   * render it without importing scope:edge directly (preserves nx-boundary).
   * SPEC-06 §3: "resolvePairingSlot() gives the onboarding host DevicePairingPageComponent"
   */
  resolvePairingSlot(): Promise<Type<DevicePairingPageComponent>> {
    return Promise.resolve(DevicePairingPageComponent);
  }

  /** Whether EDGE has been registered with the shell. */
  isRegistered(): boolean {
    return this.registered;
  }
}
