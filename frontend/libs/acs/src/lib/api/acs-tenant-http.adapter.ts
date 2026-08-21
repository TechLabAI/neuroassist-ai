import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpContext } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ACCESS_HTTP_BASE } from '@neuroassist/shared/access-http-base';
import { AcsTenantPort, TenantDto, TenantCreateDto } from './acs-tenant.port';
import { SKIP_EMPRESA_ID_HEADER } from './acs-http.interceptor';

// =============================================================================
// AcsTenantHttpAdapter — production adapter (stub compilable)
// Base path: ${ACCESS_HTTP_BASE}/v1/acs/tenants (routed via GWY).
//
// POST /tenants and POST /tenants/:id/provisioning:retry must NOT carry
// X-Empresa-Id (no active tenant exists yet when these are called).
// They are marked with SKIP_EMPRESA_ID_HEADER HttpContextToken so the
// acsHttpInterceptorFn skips adding the header for those routes.
//
// TODO[INT-FE-ACS-001]: wire once acs-service is deployed.
// =============================================================================

/** HttpContext that tells acsHttpInterceptorFn to omit X-Empresa-Id for this request. */
const skipEmpresaIdCtx = new HttpContext().set(SKIP_EMPRESA_ID_HEADER, true);

@Injectable({ providedIn: 'root' })
export class AcsTenantHttpAdapter implements AcsTenantPort {
  private readonly http = inject(HttpClient);
  private readonly base = `${ACCESS_HTTP_BASE}/v1/acs/tenants`;

  get(id: string): Observable<TenantDto> {
    return this.http.get<TenantDto>(`${this.base}/${id}`);
  }

  create(dto: TenantCreateDto): Observable<TenantDto> {
    // No X-Empresa-Id: no active tenant at provisioning time
    return this.http.post<TenantDto>(this.base, dto, { context: skipEmpresaIdCtx });
  }

  retryProvisioning(id: string): Observable<TenantDto> {
    // No X-Empresa-Id: provisioning saga may still be in flight
    return this.http.post<TenantDto>(`${this.base}/${id}/provisioning:retry`, {}, { context: skipEmpresaIdCtx });
  }
}
