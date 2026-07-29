import { Injectable } from '@angular/core';
import { Observable, of, throwError } from 'rxjs';
import { delay } from 'rxjs/operators';
import { HttpResponse, HttpErrorResponse } from '@angular/common/http';
import {
  PacComandoPort,
  Comando,
  ComandoCreate,
  ComandoIdentityEdit,
  ComandoTuning,
  ComandoEstado,
} from './comando.port';

// ---------------------------------------------------------------------------
// Mock seed — sourced from prototype shared/mock-data.ts
// All mock comandos start as 'published' (pre-existing catalog).
// ---------------------------------------------------------------------------

const MOCK_COMANDOS: Comando[] = [
  {
    id: 'cmd-ajuda',
    pacienteId: 'pac-marcio',
    rotulo: 'Ajuda',
    urgencia: 'emergencia',
    primitivoPrincipal: 'tg-sobrancelha',
    primitivoSecundario: null,
    sensibilidade: 'media',
    tempoMinimoGestoMs: 400,
    tempoMaximoGestoMs: 1500,
    estado: 'published',
    etag: 'etag-cmd-ajuda-v1',
  },
  {
    id: 'cmd-agua',
    pacienteId: 'pac-marcio',
    rotulo: 'água',
    urgencia: 'rotina',
    primitivoPrincipal: 'tg-sobrancelha',
    primitivoSecundario: 'tg-piscar-duplo',
    sensibilidade: 'alta',
    tempoMinimoGestoMs: 400,
    tempoMaximoGestoMs: 1800,
    estado: 'published',
    etag: 'etag-cmd-agua-v1',
  },
  {
    id: 'cmd-sapo',
    pacienteId: 'pac-marcio',
    rotulo: 'Sapo',
    urgencia: 'rotina',
    primitivoPrincipal: 'tg-piscar-rapido',
    primitivoSecundario: 'tg-piscar-devagar',
    sensibilidade: 'media',
    tempoMinimoGestoMs: 400,
    tempoMaximoGestoMs: 1800,
    estado: 'published',
    etag: 'etag-cmd-sapo-v1',
  },
];

let _comandos: Comando[] = [...MOCK_COMANDOS];

// ---------------------------------------------------------------------------
// ComandoMockAdapter — dev/test only. No backend required.
// Implements full lifecycle (create/updateDraft/validate/publish/recalibrate/retire).
// Emergency gate: allows all commands (no GEST eligibility check in mock).
// ---------------------------------------------------------------------------

@Injectable()
export class ComandoMockAdapter implements PacComandoPort {
  list(pid: string, estado?: ComandoEstado[]): Observable<Comando[]> {
    let result = _comandos.filter((c) => c.pacienteId === pid);
    if (estado && estado.length > 0) {
      result = result.filter((c) => estado.includes(c.estado));
    } else {
      // Default: exclude retired (mirrors BE default)
      result = result.filter((c) => c.estado !== 'retired');
    }
    return of(result).pipe(delay(400));
  }

  get(pid: string, cid: string): Observable<Comando> {
    const found = _comandos.find((c) => c.pacienteId === pid && c.id === cid);
    if (!found) {
      return throwError(
        () => new HttpErrorResponse({ status: 404, statusText: 'Not Found' }),
      );
    }
    return of(found).pipe(delay(400));
  }

  create(
    pid: string,
    draft: ComandoCreate,
    _idempotencyKey?: string,
  ): Observable<HttpResponse<Comando>> {
    const cmd: Comando = {
      id: `cmd-${Date.now()}`,
      pacienteId: pid,
      rotulo: draft.rotulo,
      urgencia: draft.urgencia,
      primitivoPrincipal: draft.primitivoPrincipal,
      primitivoSecundario: draft.primitivoSecundario,
      sensibilidade: draft.sensibilidade,
      tempoMinimoGestoMs: draft.tempoMinimoGestoMs,
      tempoMaximoGestoMs: draft.tempoMaximoGestoMs,
      estado: 'drafted',
      etag: `etag-${Date.now()}-v1`,
    };
    _comandos = [..._comandos, cmd];
    return of(new HttpResponse<Comando>({ status: 201, body: cmd })).pipe(delay(400));
  }

  updateDraft(
    _pid: string,
    cid: string,
    edit: ComandoIdentityEdit,
    _ifMatch: string,
  ): Observable<Comando> {
    _comandos = _comandos.map((c) =>
      c.id === cid && c.estado === 'drafted'
        ? { ...c, ...edit, etag: `etag-${Date.now()}-v2` }
        : c,
    );
    const updated = _comandos.find((c) => c.id === cid)!;
    return of(updated).pipe(delay(400));
  }

  validate(_pid: string, cid: string, _ifMatch: string): Observable<Comando> {
    _comandos = _comandos.map((c) =>
      c.id === cid ? { ...c, estado: 'validated' as ComandoEstado, etag: `etag-${Date.now()}-v3` } : c,
    );
    return of(_comandos.find((c) => c.id === cid)!).pipe(delay(400));
  }

  publish(_pid: string, cid: string, _ifMatch: string): Observable<Comando> {
    // Mock: allow all commands (no emergency gate in mock)
    _comandos = _comandos.map((c) =>
      c.id === cid ? { ...c, estado: 'published' as ComandoEstado, etag: `etag-${Date.now()}-v4` } : c,
    );
    return of(_comandos.find((c) => c.id === cid)!).pipe(delay(400));
  }

  recalibrate(
    _pid: string,
    cid: string,
    tuning: ComandoTuning,
    _ifMatch: string,
  ): Observable<Comando> {
    _comandos = _comandos.map((c) =>
      c.id === cid ? { ...c, ...tuning, etag: `etag-${Date.now()}-v5` } : c,
    );
    return of(_comandos.find((c) => c.id === cid)!).pipe(delay(400));
  }

  retire(_pid: string, cid: string, _ifMatch: string): Observable<Comando> {
    // Terminal — no DELETE (ADR-006). Retired records remain in history.
    _comandos = _comandos.map((c) =>
      c.id === cid ? { ...c, estado: 'retired' as ComandoEstado, etag: `etag-${Date.now()}-v6` } : c,
    );
    return of(_comandos.find((c) => c.id === cid)!).pipe(delay(400));
  }
}
