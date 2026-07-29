import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import { HttpResponse } from '@angular/common/http';

// ---------------------------------------------------------------------------
// Domain types — PAC Comando (SPEC-04 §2, api-contract §4)
// No DELETE — lifecycle is history-preserving (ADR-006): drafted → validated →
// published → (recalibrated ↻) → retired.
// ---------------------------------------------------------------------------

export type ComandoUrgencia = 'emergencia' | 'rotina';
export type ComandoSensibilidade = 'baixa' | 'media' | 'alta';
export type ComandoEstado = 'drafted' | 'validated' | 'published' | 'retired';

/** Sync state — FE-only concern, NOT in the BE response body.
 * ADR-005 (command-edit-not-live-until-synced): 'active-on-device' requires EDGE
 * device-ack. In MVP, syncState is always 'pending-sync' (stub adapter). */
export type SyncState = 'pending-sync' | 'active-on-device';

export type TestedState = 'not-tested' | 'tested';

export interface Comando {
  readonly id: string;
  readonly pacienteId: string;
  readonly rotulo: string;
  readonly urgencia: ComandoUrgencia;
  readonly primitivoPrincipal: string;          // ref → TipoGesto.id (GEST-owned)
  readonly primitivoSecundario: string | null;  // composite gesture
  readonly sensibilidade: ComandoSensibilidade;
  readonly tempoMinimoGestoMs: number;
  readonly tempoMaximoGestoMs: number;
  readonly estado: ComandoEstado;
  readonly etag: string;                        // ETag / row version for If-Match
}

export interface ComandoCreate {
  readonly rotulo: string;
  readonly urgencia: ComandoUrgencia;
  readonly primitivoPrincipal: string;
  readonly primitivoSecundario: string | null;
  readonly sensibilidade: ComandoSensibilidade;
  readonly tempoMinimoGestoMs: number;
  readonly tempoMaximoGestoMs: number;
}

/** Only editable while estado === 'drafted' */
export interface ComandoIdentityEdit {
  readonly rotulo?: string;
  readonly urgencia?: ComandoUrgencia;
  readonly primitivoPrincipal?: string;
  readonly primitivoSecundario?: string | null;
  readonly sensibilidade?: ComandoSensibilidade;
  readonly tempoMinimoGestoMs?: number;
  readonly tempoMaximoGestoMs?: number;
}

/** Recalibration payload (tuning-only, never touches identity fields) */
export interface ComandoTuning {
  readonly sensibilidade: ComandoSensibilidade;
  readonly tempoMinimoGestoMs: number;
  readonly tempoMaximoGestoMs: number;
}

// ---------------------------------------------------------------------------
// PacComandoPort — SPEC-04 §2, aligned to api-contract §4
// Scope: pac:command:read / pac:command:write / pac:command:publish
// ---------------------------------------------------------------------------

export interface PacComandoPort {
  /** GET /api/pac/v1/pacientes/{pid}/comandos?estado=<estados> */
  list(pid: string, estado?: ComandoEstado[]): Observable<Comando[]>;

  /** GET /api/pac/v1/pacientes/{pid}/comandos/{cid} */
  get(pid: string, cid: string): Observable<Comando>;

  /** POST /api/pac/v1/pacientes/{pid}/comandos (201) */
  create(
    pid: string,
    draft: ComandoCreate,
    idempotencyKey?: string,
  ): Observable<HttpResponse<Comando>>;

  /** PUT /api/pac/v1/pacientes/{pid}/comandos/{cid} (only while drafted) */
  updateDraft(
    pid: string,
    cid: string,
    edit: ComandoIdentityEdit,
    ifMatch: string,
  ): Observable<Comando>;

  /** POST /api/pac/v1/pacientes/{pid}/comandos/{cid}:validate (drafted → validated) */
  validate(pid: string, cid: string, ifMatch: string): Observable<Comando>;

  /** POST …:publish (validated → published, triggers emergency eligibility gate) */
  publish(pid: string, cid: string, ifMatch: string): Observable<Comando>;

  /** POST …:recalibrate (published → published with new tuning) */
  recalibrate(
    pid: string,
    cid: string,
    tuning: ComandoTuning,
    ifMatch: string,
  ): Observable<Comando>;

  /** POST …:retire (terminal — no DELETE, ADR-006) */
  retire(pid: string, cid: string, ifMatch: string): Observable<Comando>;
}

export const PAC_COMANDO_PORT = new InjectionToken<PacComandoPort>('PacComandoPort');
