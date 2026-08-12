import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AcsTenantPort, TenantDto, TenantCreateDto } from './acs-tenant.port';

// =============================================================================
// AcsTenantHttpAdapter — production adapter (stub compilable)
// Base path: /api/v1/acs/tenants (routed via GWY).
// Interceptors (shared/access-http-base) inject X-Empresa-Id, Authorization,
// X-Correlation-Id. Idempotency-Key generated per-call by the base interceptor.
// TODO[INT-FE-ACS-001]: wire once acs-service is deployed.
// =============================================================================

@Injectable({ providedIn: 'root' })
export class AcsTenantHttpAdapter implements AcsTenantPort {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/v1/acs/tenants';

  get(id: string): Observable<TenantDto> {
    return this.http.get<TenantDto>(`${this.base}/${id}`);
  }

  create(dto: TenantCreateDto): Observable<TenantDto> {
    return this.http.post<TenantDto>(this.base, dto);
  }

  retryProvisioning(id: string): Observable<TenantDto> {
    return this.http.post<TenantDto>(`${this.base}/${id}/provisioning:retry`, {});
  }
}
