import { Injectable } from '@angular/core';
import { Observable, of, throwError, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { DashSummaryPort } from './dash-summary.port';
import { DashSummaryResponse } from '../dash.types';

// =============================================================================
// DashSummaryMockAdapter — dev/test (default adapter)
// Seed data: Márcio Amaral (Família Amaral tenant — matches prototype mock-data.ts).
// BFF shape: GET /panel/summary response (api-contract.md §2.1).
// Delay 400ms (project standard).
// =============================================================================

// =============================================================================
// E2E flag helpers (localStorage — browser-only, best-effort)
// Flags set via page.addInitScript(() => localStorage.setItem('__DASH_E2E_*__', 'true'))
// before Angular bootstraps so the mock adapter reads them on its first call.
//
//  __DASH_E2E_SUMMARY_FAIL__ : getSummary() throws 503 → facade shows error state + retry
//  __DASH_E2E_SUMMARY_403__  : getSummary() throws 403 forbidden-scope → inline 403 banner
// =============================================================================

function e2eFlag(name: string): boolean {
  try {
    return typeof localStorage !== 'undefined' && localStorage.getItem(name) === 'true';
  } catch {
    return false;
  }
}

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
    return timer(400).pipe(
      switchMap(() => {
        // E2E: force 403 forbidden-scope to test inline 403 banner (FE-DASH-030, G8)
        if (e2eFlag('__DASH_E2E_SUMMARY_403__')) {
          return throwError(() =>
            Object.assign(new Error('Você não tem acesso a este paciente.'), {
              status: 403,
              type: 'https://neuroassist.ai/problems/forbidden-scope',
            }),
          );
        }
        // E2E: force 503 upstream failure to test error state + retry (FE-DASH-024, FE-DASH-030)
        if (e2eFlag('__DASH_E2E_SUMMARY_FAIL__')) {
          return throwError(() =>
            Object.assign(new Error('Serviço temporariamente indisponível.'), { status: 503 }),
          );
        }
        return of(MOCK_SUMMARY);
      }),
    );
  }
}
