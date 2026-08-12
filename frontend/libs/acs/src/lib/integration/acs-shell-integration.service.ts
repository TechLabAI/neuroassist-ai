import { Injectable, inject } from '@angular/core';
import { AcsTenantFacade } from '../acs-tenant.facade';
import { AcsCaregiverFacade } from '../acs-caregiver.facade';

// =============================================================================
// AcsShellIntegrationService — SPEC-06 (shell integration)
// Consumed by scope:web shell to bootstrap ACS before rendering any ACS route.
// Exposes the active tenant and coverage state to shell-level components (topbar etc.).
// =============================================================================

@Injectable({ providedIn: 'root' })
export class AcsShellIntegrationService {
  private readonly tenantFacade = inject(AcsTenantFacade);
  private readonly caregiverFacade = inject(AcsCaregiverFacade);

  /** Call once from AppShellComponent.ngOnInit() after provideAcs() is registered.
   * Loads the active tenant and the caregiver roster (to derive coverage state). */
  async bootstrap(activeTenantId: string): Promise<void> {
    await Promise.all([
      this.tenantFacade.load(activeTenantId),
      this.caregiverFacade.loadRoster(),
    ]);
  }

  /** Active tenant view-model (null until bootstrap completes). */
  get tenant() {
    return this.tenantFacade.tenant;
  }

  /** Coverage for a specific patient (derived client-side). */
  coverageFor(pacienteId: string) {
    return this.caregiverFacade.coverageFor(pacienteId);
  }
}
