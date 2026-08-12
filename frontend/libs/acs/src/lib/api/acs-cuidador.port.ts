import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';

// =============================================================================
// AcsCuidadorPort — SPEC-04 §2 (data-access contract)
// Abstraction over the acs-service /caregivers API.
// =============================================================================

// ---------------------------------------------------------------------------
// Wire DTOs (mirror BE api-contract §4)
// ---------------------------------------------------------------------------

export type CuidadorPapel = 'cuidadora' | 'coordenador_clinica';
export type CuidadorStatus = 'active' | 'inactive';

export interface CuidadorDto {
  readonly id: string;
  readonly tenantId: string;
  readonly nome: string;
  readonly email: string;
  readonly papel: CuidadorPapel;
  readonly status: CuidadorStatus;
}

export interface CuidadorCreateDto {
  readonly nome: string;
  readonly email: string;
  readonly papel: CuidadorPapel;
}

// ---------------------------------------------------------------------------
// Port interface
// ---------------------------------------------------------------------------

export interface AcsCuidadorPort {
  /** GET /caregivers — list all caregivers for the active tenant. */
  list(): Observable<CuidadorDto[]>;

  /** GET /caregivers/{id} — read one caregiver. */
  get(id: string): Observable<CuidadorDto>;

  /** POST /caregivers — invite/register a caregiver.
   * BE expects Idempotency-Key header (injected by HTTP base). */
  create(dto: CuidadorCreateDto): Observable<CuidadorDto>;

  /** POST /caregivers/{id}/deactivation — soft-deactivate (never hard-delete). */
  deactivate(id: string): Observable<CuidadorDto>;
}

export const ACS_CUIDADOR_PORT = new InjectionToken<AcsCuidadorPort>('AcsCuidadorPort');
