import { Injectable } from '@angular/core';
import { Observable, of, throwError, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { HistClientPort } from './hist-client.port';
import {
  AuditRecordResponse,
  ChainVerificationResult,
  HistoryQueryParams,
  PagedResponse,
} from '../hist.types';

// =============================================================================
// HistClientMockAdapter — dev/test (default adapter)
// Seed data: Márcio Amaral (Família Amaral tenant — matches prototype mock-data.ts).
// Delay 400ms (project standard).
// =============================================================================

// A âncora dos registros semeados.
//
// ERA uma data fixa (`2026-08-16T14:00:00Z`), e isso é uma bomba-relógio: a
// janela "Semana" do facade é ROLANTE (`now - 7 dias` .. `now`), então a semente
// foi saindo dela sozinha com a passagem dos dias. Em 24/08 o `/historico`
// mostrava "Nenhum evento neste período" e oito cenários reprovavam — sem que
// nada no aplicativo tivesse mudado.
//
// Ancorar em `new Date()` faz os deslocamentos abaixo (0, 1, 3 e 8 dias) valerem
// para sempre: três registros dentro da semana, um só dentro do mês. Como a
// janela é rolante e não de calendário, não há problema de virada de meia-noite.
const NOW = new Date();

function isoNDaysAgo(n: number): string {
  const d = new Date(NOW.getTime() - n * 24 * 60 * 60 * 1000);
  return d.toISOString();
}

const MOCK_RECORDS: AuditRecordResponse[] = [
  {
    id: 'rec-001',
    factId: 'fact-001',
    factType: 'evento.confirmado',
    subject: { type: 'paciente', id: 'pac-marcio' },
    actor: { caregiverId: 'cuid-jamilly', displayNameSnapshot: 'Jamilly Amaral' },
    payload: { eventoId: 'evt-001', rotulo: 'água', urgencia: 'rotina', verdict: null },
    occurredAt: isoNDaysAgo(0),
    recordedAt: isoNDaysAgo(0),
    streamKey: 'paciente:pac-marcio',
    sequenceNo: 1,
    prevHash: '0'.repeat(64),
    recordHash: 'a1b2c3d4' + '0'.repeat(56),
    correctsRecordId: null,
  },
  {
    id: 'rec-002',
    factId: 'fact-002',
    factType: 'evento.confirmado',
    subject: { type: 'paciente', id: 'pac-marcio' },
    actor: { caregiverId: 'cuid-jamilly', displayNameSnapshot: 'Jamilly Amaral' },
    payload: { eventoId: 'evt-002', rotulo: 'Sapo', urgencia: 'emergencia', verdict: 'real' },
    occurredAt: isoNDaysAgo(1),
    recordedAt: isoNDaysAgo(1),
    streamKey: 'paciente:pac-marcio',
    sequenceNo: 2,
    prevHash: 'a1b2c3d4' + '0'.repeat(56),
    recordHash: 'b2c3d4e5' + '0'.repeat(56),
    correctsRecordId: null,
  },
  {
    id: 'rec-003',
    factId: 'fact-003',
    factType: 'evento.confirmado',
    subject: { type: 'paciente', id: 'pac-marcio' },
    actor: { caregiverId: 'cuid-jamilly', displayNameSnapshot: 'Jamilly Amaral' },
    payload: { eventoId: 'evt-003', rotulo: 'frio', urgencia: 'rotina', verdict: 'falso_positivo' },
    occurredAt: isoNDaysAgo(3),
    recordedAt: isoNDaysAgo(3),
    streamKey: 'paciente:pac-marcio',
    sequenceNo: 3,
    prevHash: 'b2c3d4e5' + '0'.repeat(56),
    recordHash: 'c3d4e5f6' + '0'.repeat(56),
    correctsRecordId: null,
  },
  {
    id: 'rec-004',
    factId: 'fact-004',
    factType: 'evento.confirmado',
    subject: { type: 'paciente', id: 'pac-marcio' },
    actor: { caregiverId: 'cuid-jamilly', displayNameSnapshot: 'Jamilly Amaral' },
    payload: { eventoId: 'evt-004', rotulo: 'dor', urgencia: 'emergencia', verdict: null },
    occurredAt: isoNDaysAgo(8),
    recordedAt: isoNDaysAgo(8),
    streamKey: 'paciente:pac-marcio',
    sequenceNo: 4,
    prevHash: 'c3d4e5f6' + '0'.repeat(56),
    recordHash: 'd4e5f6a7' + '0'.repeat(56),
    correctsRecordId: null,
  },
];

function filterByWindow(
  records: AuditRecordResponse[],
  params: HistoryQueryParams,
): AuditRecordResponse[] {
  const from = new Date(params.from).getTime();
  const to = new Date(params.to).getTime();
  return records.filter((r) => {
    const t = new Date(r.recordedAt).getTime();
    return t >= from && t < to;
  });
}

// =============================================================================
// E2E flag helpers (localStorage — browser-only, best-effort)
// Flags set via page.addInitScript(() => localStorage.setItem('__HIST_E2E_*__', 'true'))
// before Angular bootstraps so the mock adapter reads them on its first call.
//
//  __HIST_E2E_LIST_FAIL__   : list() throws a non-HTTP error → facade shows error state
//  __HIST_E2E_SMALL_PAGE__  : list() returns size=2 so hasMore=true with ≥3 semana records
//  __HIST_E2E_EMPTY__       : list() returns [] — o estado vazio, de propósito
// =============================================================================

function e2eFlag(name: string): boolean {
  try {
    return typeof localStorage !== 'undefined' && localStorage.getItem(name) === 'true';
  } catch {
    return false;
  }
}

@Injectable({ providedIn: 'root' })
export class HistClientMockAdapter implements HistClientPort {
  list(params: HistoryQueryParams): Observable<PagedResponse<AuditRecordResponse>> {
    return timer(400).pipe(
      switchMap(() => {
        // E2E: force error to test error state + retry affordance
        if (e2eFlag('__HIST_E2E_LIST_FAIL__')) {
          return throwError(() =>
            Object.assign(new Error('E2E simulated list failure'), { status: 503 }),
          );
        }

        // O estado vazio precisa ser PEDIDO, e não herdado de uma janela que por
        // acaso não pega nada. Os dois cenários de "período vazio" dependiam de a
        // semente ser antiga demais para a janela "Hoje" — quando a âncora passou
        // a ser o presente, eles reprovaram. Um teste que só passa enquanto o
        // mock envelhece está medindo o calendário, não o aplicativo.
        if (e2eFlag('__HIST_E2E_EMPTY__')) {
          return of({ items: [], page: 0, size: params.size ?? 50, total: 0 });
        }

        const filtered = filterByWindow(MOCK_RECORDS, params);
        const order = params.order ?? 'desc';
        const sorted = [...filtered].sort((a, b) =>
          order === 'desc'
            ? b.recordedAt.localeCompare(a.recordedAt)
            : a.recordedAt.localeCompare(b.recordedAt),
        );
        const page = params.page ?? 0;
        // E2E: small page size so hasMore becomes true with ≥3 records (semana window)
        const size = e2eFlag('__HIST_E2E_SMALL_PAGE__') ? 2 : (params.size ?? 50);
        const sliced = sorted.slice(page * size, (page + 1) * size);
        return of({ items: sliced, page, size, total: sorted.length });
      }),
    );
  }

  get(id: string): Observable<AuditRecordResponse> {
    return timer(400).pipe(
      switchMap(() => {
        const rec = MOCK_RECORDS.find((r) => r.id === id);
        if (!rec) throw new Error(`mock: hist record not found: ${id}`);
        return of(rec);
      }),
    );
  }

  verify(subjectType: string, subjectId: string): Observable<ChainVerificationResult> {
    return timer(600).pipe(
      switchMap(() =>
        of({
          streamKey: `${subjectType}:${subjectId}`,
          status: 'intact' as const,
          recordsVerified: MOCK_RECORDS.length,
          brokenAtSequenceNo: null,
          verifiedAt: NOW.toISOString(),
        }),
      ),
    );
  }
}
