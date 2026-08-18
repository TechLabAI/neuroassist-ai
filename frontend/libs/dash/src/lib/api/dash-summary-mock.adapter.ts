import { Injectable } from '@angular/core';
import { Observable, of, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { DashSummaryPort } from './dash-summary.port';
import { DashSummaryResponse } from '../dash.types';

// =============================================================================
// DashSummaryMockAdapter — dev/test (default adapter)
// Seed data: Márcio Amaral (Família Amaral tenant — matches prototype mock-data.ts).
// BFF shape: GET /panel/summary response (api-contract.md §2.1).
// Delay 400ms (project standard).
// =============================================================================

const MOCK_SUMMARY: DashSummaryResponse = {
  tenantId: 'tenant-familia-amaral',
  caregiver: {
    id: 'cuid-jamilly',
    name: 'Jamilly Amaral',
    role: 'cuidadora',
  },
  patients: [
    {
      patientId: 'pac-marcio',
      name: 'Márcio Amaral',
      device: {
        status: 'online',
        lastSeenAt: new Date(Date.now() - 3 * 60 * 1000).toISOString(), // 3 min ago
        degraded: false,
      },
      latestEvent: {
        eventId: 'evt-009',
        commandLabel: 'Ajuda',
        urgencia: 'emergencia',
        feedback: 'nao_avaliado',
        at: new Date(Date.now() - 20 * 60 * 1000).toISOString(), // 20 min ago
      },
      unacknowledgedCount: 1,
    },
  ],
  degradedSections: [],
};

@Injectable({ providedIn: 'root' })
export class DashSummaryMockAdapter implements DashSummaryPort {
  getSummary(): Observable<DashSummaryResponse> {
    return timer(400).pipe(switchMap(() => of(MOCK_SUMMARY)));
  }
}
