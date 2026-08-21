import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ACCESS_HTTP_BASE } from '@neuroassist/shared/access-http-base';
import { AcsCuidadorPort, CuidadorDto, CuidadorCreateDto } from './acs-cuidador.port';

// =============================================================================
// AcsCuidadorHttpAdapter — production adapter (stub compilable)
// Base path: ${ACCESS_HTTP_BASE}/v1/acs/caregivers
// TODO[INT-FE-ACS-001]: X-Empresa-Id header via shared interceptor quando promovido de Wave-0 stub.
// TODO[INT-FE-ACS-002]: wire once acs-service is deployed.
// =============================================================================

@Injectable({ providedIn: 'root' })
export class AcsCuidadorHttpAdapter implements AcsCuidadorPort {
  private readonly http = inject(HttpClient);
  private readonly base = `${ACCESS_HTTP_BASE}/v1/acs/caregivers`;

  list(): Observable<CuidadorDto[]> {
    return this.http.get<CuidadorDto[]>(this.base);
  }

  get(id: string): Observable<CuidadorDto> {
    return this.http.get<CuidadorDto>(`${this.base}/${id}`);
  }

  create(dto: CuidadorCreateDto): Observable<CuidadorDto> {
    return this.http.post<CuidadorDto>(this.base, dto);
  }

  deactivate(id: string): Observable<CuidadorDto> {
    return this.http.post<CuidadorDto>(`${this.base}/${id}/deactivation`, {});
  }
}
