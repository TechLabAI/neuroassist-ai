import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import { PacPacientePort, Paciente, PacienteCreate, PacienteUpdate, Page } from './paciente.port';

// ---------------------------------------------------------------------------
// PacienteHttpAdapter — stub compilável (Layer 2 implementa os corpos).
// Base path: /api/pac/v1/pacientes (roteado via GWY, descoberto via REG/Eureka).
// Mandatory headers (X-Empresa-Id, X-Correlation-Id, Authorization) são
// injetados pelos interceptors HTTP de @neuroassist/shared/access-http-base.
// ---------------------------------------------------------------------------

@Injectable()
export class PacienteHttpAdapter implements PacPacientePort {
  private readonly http = inject(HttpClient);
  private readonly base = '/api/pac/v1/pacientes';

  list(page = 0, size = 20): Observable<Page<Paciente>> {
    // TODO[PAC-FE-007]: implement with HttpClient.get + query params
    return this.http.get<Page<Paciente>>(`${this.base}?page=${page}&size=${size}`);
  }

  get(id: string): Observable<Paciente> {
    // TODO[PAC-FE-007]: implement
    return this.http.get<Paciente>(`${this.base}/${id}`);
  }

  create(dto: PacienteCreate, idempotencyKey?: string): Observable<HttpResponse<Paciente>> {
    // TODO[PAC-FE-008]: add Idempotency-Key header
    const headers = idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {};
    return this.http.post<Paciente>(this.base, dto, {
      headers,
      observe: 'response',
    });
  }

  update(id: string, dto: PacienteUpdate, ifMatch: string): Observable<Paciente> {
    // TODO[PAC-FE-008]: If-Match is added by an HTTP interceptor (shared/access-http-base)
    // or manually here for resource-specific ops.
    return this.http.put<Paciente>(`${this.base}/${id}`, dto, {
      headers: { 'If-Match': ifMatch },
    });
  }
}
