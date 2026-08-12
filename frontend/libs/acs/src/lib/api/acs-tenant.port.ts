import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';

// =============================================================================
// AcsTenantPort — SPEC-04 §1 (data-access contract)
// Abstraction over the acs-service /tenants API.
// Implementations: AcsTenantMockAdapter (dev) / AcsTenantHttpAdapter (prod).
// Base path /api/v1/acs/tenants routed by GWY; interceptors inject X-Empresa-Id + Auth.
// =============================================================================

// ---------------------------------------------------------------------------
// Wire DTOs (mirror BE api-contract §3)
// ---------------------------------------------------------------------------

export type TenantTipo = 'familia' | 'clinica';
export type ProvisioningStatus = 'active' | 'provisioning' | 'failed';

export interface TenantDto {
  readonly id: string;
  readonly nome: string;
  readonly tipo: TenantTipo;
  readonly provisioningStatus: ProvisioningStatus;
}

export interface TenantCreateDto {
  readonly nome: string;
  readonly tipo: TenantTipo;
}

// ---------------------------------------------------------------------------
// Port interface
// ---------------------------------------------------------------------------

export interface AcsTenantPort {
  /** GET /tenants/{id} — read active tenant record. */
  get(id: string): Observable<TenantDto>;

  /** POST /tenants — provision a new tenant (empresa/família ou clínica).
   * Returns 201 (active) or 202 (provisioning saga in progress, BE §3.3). */
  create(dto: TenantCreateDto): Observable<TenantDto>;

  /** POST /tenants/{id}/provisioning:retry — retry failed saga step. */
  retryProvisioning(id: string): Observable<TenantDto>;
}

export const ACS_TENANT_PORT = new InjectionToken<AcsTenantPort>('AcsTenantPort');
