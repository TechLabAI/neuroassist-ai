import { Injectable } from '@angular/core';
import { Observable, of, timer } from 'rxjs';
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

const NOW = new Date('2026-08-16T14:00:00Z');

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

@Injectable({ providedIn: 'root' })
export class HistClientMockAdapter implements HistClientPort {
  list(params: HistoryQueryParams): Observable<PagedResponse<AuditRecordResponse>> {
    return timer(400).pipe(
      switchMap(() => {
        const filtered = filterByWindow(MOCK_RECORDS, params);
        const order = params.order ?? 'desc';
        const sorted = [...filtered].sort((a, b) =>
          order === 'desc'
            ? b.recordedAt.localeCompare(a.recordedAt)
            : a.recordedAt.localeCompare(b.recordedAt),
        );
        const page = params.page ?? 0;
        const size = params.size ?? 50;
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
