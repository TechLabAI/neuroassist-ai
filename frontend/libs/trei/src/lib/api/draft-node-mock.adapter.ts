import { Injectable } from '@angular/core';
import { Observable, of, timer, throwError } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { DraftNodePort } from './draft-node.port';
import {
  MaterializeRequest,
  DraftNodeCreated,
  NodeDetail,
  TreiLocale,
} from '../trei.types';

// =============================================================================
// DraftNodeMockAdapter — dev/test (default adapter)
// In-memory store of materialized draft nodes.
// Seed data: empty on init (nodes are created live via materialize()).
// Delay 400ms (project standard).
// =============================================================================

interface StoredNode {
  noId: string;
  pacienteId: string;
  tipo: 'frase' | 'categoria';
  parentNoId: string;
  locales: Map<TreiLocale, {
    texto: string;
    subEstado: string;
    confirmadoCuidadora: boolean;
    confirmadoPaciente: boolean;
  }>;
}

let nodeCounter = 0;
const nodeStore = new Map<string, StoredNode>();

function e2eFlag(name: string): boolean {
  try {
    return typeof localStorage !== 'undefined' && localStorage.getItem(name) === 'true';
  } catch {
    return false;
  }
}

@Injectable({ providedIn: 'root' })
export class DraftNodeMockAdapter implements DraftNodePort {
  materialize(request: MaterializeRequest): Observable<DraftNodeCreated> {
    return timer(400).pipe(
      switchMap(() => {
        nodeCounter++;
        const noId = `n-mock-${nodeCounter}`;

        // Locale-invariant node structure: same noId reused across locales (ADR-018)
        const existing = nodeStore.get(noId);
        const stored: StoredNode = existing ?? {
          noId,
          pacienteId: request.pacienteId,
          tipo: request.tipo,
          parentNoId: request.parentNoId,
          locales: new Map(),
        };

        stored.locales.set(request.locale, {
          texto: request.texto,
          subEstado: 'rascunho',
          confirmadoCuidadora: false,
          confirmadoPaciente: false,
        });

        nodeStore.set(noId, stored);

        const result: DraftNodeCreated = {
          noId,
          locale: request.locale,
          subEstado: 'rascunho',
          confirmadoCuidadora: false,
          confirmadoPaciente: false,
          contentStatus: 'pendente',
        };

        return of(result);
      }),
    );
  }

  inspect(noId: string): Observable<NodeDetail> {
    return timer(400).pipe(
      switchMap(() => {
        const stored = nodeStore.get(noId);
        if (!stored) {
          return throwError(() =>
            Object.assign(new Error(`mock: node not found: ${noId}`), { status: 404 }),
          );
        }

        const detail: NodeDetail = {
          noId: stored.noId,
          pacienteId: stored.pacienteId,
          tipo: stored.tipo,
          parentNoId: stored.parentNoId,
          locales: Array.from(stored.locales.entries()).map(([locale, data]) => ({
            locale,
            texto: data.texto,
            subEstado: data.subEstado as any,
            confirmadoCuidadora: data.confirmadoCuidadora,
            confirmadoPaciente: data.confirmadoPaciente,
            audioRef: { present: false },
          })),
        };

        return of(detail);
      }),
    );
  }

  discard(noId: string, locale: TreiLocale): Observable<{ noId: string; locale: TreiLocale; subEstado: string }> {
    return timer(400).pipe(
      switchMap(() => {
        if (e2eFlag('__TREI_E2E_DISCARD_FAIL__')) {
          return throwError(() =>
            Object.assign(new Error('E2E: already-published'), { status: 409 }),
          );
        }

        const stored = nodeStore.get(noId);
        if (stored) {
          const localeData = stored.locales.get(locale);
          if (localeData) {
            localeData.subEstado = 'descartado';
          }
        }

        return of({ noId, locale, subEstado: 'descartado' });
      }),
    );
  }
}
