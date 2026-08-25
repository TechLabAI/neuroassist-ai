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

/**
 * Corpo de `PUT /caregivers/{id}` — corrigir nome e login.
 *
 * <p><b>Sem `papel`, e a diferença para o `CuidadorCreateDto` é deliberada.</b> O
 * backend aceita só nome e login nesta operação: papel muda o que a pessoa pode
 * fazer no sistema inteiro, senha é poder de assumir a conta de alguém, e status
 * tem endpoint próprio que carrega a invariante D-204. Repetir a forma do
 * cadastro aqui faria a tela oferecer campos que o servidor ignoraria em
 * silêncio — a pessoa mudaria o papel, veria "salvo", e nada teria mudado.
 */
export interface CuidadorEditDto {
  readonly nome: string;
  readonly email: string;
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

  /**
   * `PUT /caregivers/{id}` — corrige nome e login.
   *
   * <p>Existe desde que a capacidade passou a existir no acs-service. Antes, a
   * tela de edição tinha formulário preenchido e um "Salvar" que NÃO FAZIA NADA:
   * o `if` do componente cobria só a criação e não havia `else`.
   */
  edit(id: string, dto: CuidadorEditDto): Observable<CuidadorDto>;

  /** POST /caregivers/{id}/deactivation — soft-deactivate (never hard-delete). */
  deactivate(id: string): Observable<CuidadorDto>;
}

export const ACS_CUIDADOR_PORT = new InjectionToken<AcsCuidadorPort>('AcsCuidadorPort');
