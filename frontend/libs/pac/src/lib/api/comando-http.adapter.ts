import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  PacComandoPort,
  Comando,
  ComandoCreate,
  ComandoIdentityEdit,
  ComandoTuning,
  ComandoEstado,
} from './comando.port';

// ---------------------------------------------------------------------------
// ComandoHttpAdapter — stub compilável (Layer 2 implementa os corpos).
// Base path: /api/pac/v1/pacientes/{pid}/comandos
// If-Match header guards lifecycle transitions against concurrent edits.
// Emergency gate (422 /errors/emergency-not-detectable) is surfaced via the
// error handler in PacComandoFacade — see SPEC-07.
// ---------------------------------------------------------------------------

@Injectable()
export class ComandoHttpAdapter implements PacComandoPort {
  private readonly http = inject(HttpClient);

  private baseUrl(pid: string): string {
    return `/api/pac/v1/pacientes/${pid}/comandos`;
  }

  list(pid: string, estado?: ComandoEstado[]): Observable<Comando[]> {
    const q = estado?.length ? `?estado=${estado.join(',')}` : '';
    // TODO[PAC-FE-009]: implement
    return this.http.get<Comando[]>(`${this.baseUrl(pid)}${q}`);
  }

  get(pid: string, cid: string): Observable<Comando> {
    // TODO[PAC-FE-009]: implement
    return this.http.get<Comando>(`${this.baseUrl(pid)}/${cid}`);
  }

  create(pid: string, draft: ComandoCreate, idempotencyKey?: string): Observable<HttpResponse<Comando>> {
    const headers = idempotencyKey ? { 'Idempotency-Key': idempotencyKey } : {};
    // TODO[PAC-FE-010]: implement
    return this.http.post<Comando>(this.baseUrl(pid), draft, {
      headers,
      observe: 'response',
    });
  }

  updateDraft(
    pid: string,
    cid: string,
    edit: ComandoIdentityEdit,
    ifMatch: string,
  ): Observable<Comando> {
    // TODO[PAC-FE-010]: implement
    return this.http.put<Comando>(`${this.baseUrl(pid)}/${cid}`, edit, {
      headers: { 'If-Match': ifMatch },
    });
  }

  validate(pid: string, cid: string, ifMatch: string): Observable<Comando> {
    // TODO[PAC-FE-011]: implement
    return this.http.post<Comando>(
      `${this.baseUrl(pid)}/${cid}:validate`,
      {},
      { headers: { 'If-Match': ifMatch } },
    );
  }

  publish(pid: string, cid: string, ifMatch: string): Observable<Comando> {
    // TODO[PAC-FE-011]: emergency gate 422 → blocking modal (SPEC-07, PAC-FE-021)
    return this.http.post<Comando>(
      `${this.baseUrl(pid)}/${cid}:publish`,
      {},
      { headers: { 'If-Match': ifMatch } },
    );
  }

  recalibrate(
    pid: string,
    cid: string,
    tuning: ComandoTuning,
    ifMatch: string,
  ): Observable<Comando> {
    // TODO[PAC-FE-012]: implement
    return this.http.post<Comando>(
      `${this.baseUrl(pid)}/${cid}:recalibrate`,
      tuning,
      { headers: { 'If-Match': ifMatch } },
    );
  }

  retire(pid: string, cid: string, ifMatch: string): Observable<Comando> {
    // No DELETE — terminal transition (ADR-006, history-preserving)
    // TODO[PAC-FE-012]: implement
    return this.http.post<Comando>(
      `${this.baseUrl(pid)}/${cid}:retire`,
      {},
      { headers: { 'If-Match': ifMatch } },
    );
  }
}
