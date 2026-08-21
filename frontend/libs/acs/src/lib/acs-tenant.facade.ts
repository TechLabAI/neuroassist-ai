import { Injectable, inject, signal } from '@angular/core';
import { lastValueFrom } from 'rxjs';
import { ACS_TENANT_PORT } from './api/acs-tenant.port';
import type { TenantCreateDto } from './api/acs-tenant.port';
import { TenantVm, toTenantVm } from './acs.types';
import { AcsEmpresaContextService } from './api/acs-empresa-context.service';

// =============================================================================
// AcsTenantFacade — SPEC-03 §2, SPEC-04 §1 / INT-FE-ACS-003
//
// Manages the active tenant context. ACS is the SOLE WRITER of empresa-context;
// every other module reads it from the shared signal via AcsEmpresaContextService.
//
// Write rules (INT-FE-ACS-003):
//   create()   → 201 active   → write empresaId (provisioningStatus === 'active')
//   create()   → 202 saga     → DO NOT write (empresa not yet active)
//   retryProvisioning() → active → write empresaId
//   destroy()  → DO NOT clear (peers depend on id being stable for the session)
// =============================================================================

@Injectable({ providedIn: 'root' })
export class AcsTenantFacade {
  private readonly port = inject(ACS_TENANT_PORT);
  private readonly empresaContext = inject(AcsEmpresaContextService);

  private readonly _tenant = signal<TenantVm | null>(null);
  private readonly _loading = signal(false);
  private readonly _error = signal<string | null>(null);
  private readonly _provisioning = signal(false);

  readonly tenant = this._tenant.asReadonly();
  readonly loading = this._loading.asReadonly();
  readonly error = this._error.asReadonly();
  /** True while a provisioning saga is in progress (202 response from BE). */
  readonly provisioning = this._provisioning.asReadonly();

  async load(tenantId: string): Promise<void> {
    this._loading.set(true);
    this._error.set(null);
    try {
      const dto = await lastValueFrom(this.port.get(tenantId));
      this._tenant.set(toTenantVm(dto));
      this._provisioning.set(dto.provisioningStatus === 'provisioning');
      // Restore empresa context on page reload if tenant is already active
      if (dto.provisioningStatus === 'active') {
        this.empresaContext.setActiveEmpresaId(dto.id);
      }
    } catch (err) {
      this._error.set(err instanceof Error ? err.message : 'Erro ao carregar unidade de cuidado');
    } finally {
      this._loading.set(false);
    }
  }

  async create(dto: TenantCreateDto): Promise<TenantVm | null> {
    this._loading.set(true);
    this._error.set(null);
    try {
      const result = await lastValueFrom(this.port.create(dto));
      const vm = toTenantVm(result);
      this._tenant.set(vm);
      this._provisioning.set(result.provisioningStatus === 'provisioning');
      // INT-FE-ACS-003: write empresa context ONLY on 201 active (not 202 provisioning)
      if (result.provisioningStatus === 'active') {
        this.empresaContext.setActiveEmpresaId(result.id);
      }
      return vm;
    } catch (err) {
      this._error.set(err instanceof Error ? err.message : 'Erro ao criar unidade de cuidado');
      return null;
    } finally {
      this._loading.set(false);
    }
  }

  async retryProvisioning(tenantId: string): Promise<void> {
    this._loading.set(true);
    this._error.set(null);
    try {
      const dto = await lastValueFrom(this.port.retryProvisioning(tenantId));
      this._tenant.set(toTenantVm(dto));
      this._provisioning.set(dto.provisioningStatus === 'provisioning');
      // INT-FE-ACS-003: write empresa context when retry resolves to active
      if (dto.provisioningStatus === 'active') {
        this.empresaContext.setActiveEmpresaId(dto.id);
      }
    } catch (err) {
      this._error.set(err instanceof Error ? err.message : 'Erro ao retentar provisionamento');
    } finally {
      this._loading.set(false);
    }
  }
}
