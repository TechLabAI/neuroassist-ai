import { Injectable, inject, signal } from '@angular/core';
import { lastValueFrom } from 'rxjs';
import { ACS_TENANT_PORT } from './api/acs-tenant.port';
import type { TenantCreateDto } from './api/acs-tenant.port';
import { TenantVm, toTenantVm } from './acs.types';

// =============================================================================
// AcsTenantFacade — SPEC-03 §2, SPEC-04 §1
// Manages the active tenant context. ACS is the WRITER of empresa-context;
// every other module reads it from @neuroassist/shared/empresa-context.
// Handles 201 (active) and 202 (provisioning saga) create responses.
// =============================================================================

@Injectable({ providedIn: 'root' })
export class AcsTenantFacade {
  private readonly port = inject(ACS_TENANT_PORT);

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
      // TODO[FE-ACS-002]: write tenant.id to @neuroassist/shared/empresa-context
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
    } catch (err) {
      this._error.set(err instanceof Error ? err.message : 'Erro ao retentar provisionamento');
    } finally {
      this._loading.set(false);
    }
  }
}
