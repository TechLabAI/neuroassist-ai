import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import { HttpResponse } from '@angular/common/http';

// ---------------------------------------------------------------------------
// Domain types — PAC Paciente (SPEC-04 §2, api-contract §3)
// ---------------------------------------------------------------------------

export interface Paciente {
  readonly id: string;
  readonly tenantId: string;
  readonly nome: string;
  readonly dispositivo: string | null;    // ref → DispositivoEdge (EDGE-owned)
  readonly catalogoVersao?: string;       // opaque ETag-stable value (api-contract §5 note)
}

export interface PacienteCreate {
  readonly nome: string;
}

export interface PacienteUpdate {
  readonly nome: string;
}

export interface Page<T> {
  readonly content: T[];
  readonly totalElements: number;
  readonly totalPages: number;
  readonly number: number;
  readonly size: number;
}

// ---------------------------------------------------------------------------
// PacPacientePort — SPEC-04 §2, aligned to api-contract §3
// Scope: pac:patient:read / pac:patient:write
// All calls carry X-Empresa-Id (injected by HTTP interceptor).
// ---------------------------------------------------------------------------

export interface PacPacientePort {
  /** GET /api/pac/v1/pacientes — (pac:patient:read) */
  list(page?: number, size?: number): Observable<Page<Paciente>>;

  /** GET /api/pac/v1/pacientes/{id} */
  get(id: string): Observable<Paciente>;

  /** POST /api/pac/v1/pacientes (201, Location, ETag) — idempotent with Idempotency-Key */
  create(dto: PacienteCreate, idempotencyKey?: string): Observable<HttpResponse<Paciente>>;

  /** PUT /api/pac/v1/pacientes/{id} (If-Match = ETag) */
  update(id: string, dto: PacienteUpdate, ifMatch: string): Observable<Paciente>;
}

export const PAC_PACIENTE_PORT = new InjectionToken<PacPacientePort>('PacPacientePort');
