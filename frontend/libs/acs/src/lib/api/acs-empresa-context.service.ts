import { Injectable, signal } from '@angular/core';

// =============================================================================
// AcsEmpresaContextService — INT-FE-ACS-003 / SPEC-04 §3
//
// ACS is the SOLE WRITER of the active empresa (tenant) context within the
// frontend. This service holds the id of the active tenant as a signal and
// exposes it to:
//   - AcsHttpInterceptorFn (attaches X-Empresa-Id header to ACS requests)
//   - Any ACS component that needs to read the active tenant id
//
// Write path  : AcsTenantFacade.create() and retryProvisioning() on 201/active
// Read path   : acsHttpInterceptorFn reads empresaId()
//
// IMPORTANT: destroy() of ACS MUST NOT call setActiveEmpresaId(null) — peer
// modules (EVNT, PAC, EDGE) depend on the id being stable for the session.
// =============================================================================

@Injectable({ providedIn: 'root' })
export class AcsEmpresaContextService {
  private readonly _empresaId = signal<string | null>(null);

  /** The active empresa (tenant) id — null until AcsTenantFacade writes it on successful provisioning. */
  readonly empresaId = this._empresaId.asReadonly();

  /**
   * Set the active empresa id.
   * Called by AcsTenantFacade when provisioning completes with status 'active'.
   * MUST NOT be called by any other module — ACS is the sole writer.
   *
   * @param id - Tenant id from the 201 active response, or null to clear (logout only).
   */
  setActiveEmpresaId(id: string | null): void {
    this._empresaId.set(id);
  }
}
