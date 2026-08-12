import { Injectable } from '@angular/core';
import { Observable, of, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { AcsTenantPort, TenantDto, TenantCreateDto, ProvisioningStatus } from './acs-tenant.port';

// =============================================================================
// AcsTenantMockAdapter — dev/test (default adapter)
// Seed: Família Amaral (matches prototype mock-data.ts).
// Delay 400ms on reads; 800ms on writes.
// =============================================================================

const _tenants = new Map<string, TenantDto>([
  ['tenant-familia-amaral', {
    id: 'tenant-familia-amaral',
    nome: 'Família Amaral',
    tipo: 'familia',
    provisioningStatus: 'active' as ProvisioningStatus,
  }],
]);

let _idSeq = 1;

@Injectable({ providedIn: 'root' })
export class AcsTenantMockAdapter implements AcsTenantPort {
  get(id: string): Observable<TenantDto> {
    return timer(400).pipe(
      switchMap(() => {
        const t = _tenants.get(id);
        if (!t) throw new Error(`mock: tenant not found: ${id}`);
        return of(t);
      }),
    );
  }

  create(dto: TenantCreateDto): Observable<TenantDto> {
    return timer(800).pipe(
      switchMap(() => {
        const id = `tenant-${Date.now()}-${++_idSeq}`;
        const tenant: TenantDto = { id, nome: dto.nome, tipo: dto.tipo, provisioningStatus: 'active' };
        _tenants.set(id, tenant);
        return of(tenant);
      }),
    );
  }

  retryProvisioning(id: string): Observable<TenantDto> {
    return timer(600).pipe(
      switchMap(() => {
        const t = _tenants.get(id);
        if (!t) throw new Error(`mock: tenant not found: ${id}`);
        const updated: TenantDto = { ...t, provisioningStatus: 'active' };
        _tenants.set(id, updated);
        return of(updated);
      }),
    );
  }
}
