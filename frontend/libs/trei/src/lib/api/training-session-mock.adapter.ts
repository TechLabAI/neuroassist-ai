import { Injectable } from '@angular/core';
import { Observable, of, throwError, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { TrainingSessionPort } from './training-session.port';
import { StartSessionRequest, TrainingSession } from '../trei.types';

// =============================================================================
// TrainingSessionMockAdapter — dev/test (default adapter)
// Simulates POST /api/v1/trei/sessoes with 400ms delay (project standard).
// Seed: Márcio Amaral (pac-marcio) — Família Amaral tenant.
//
// E2E control flags (localStorage):
//   __TREI_E2E_FAST__              → reduces delay to 200ms
//   __TREI_E2E_PATIENT_NOT_FOUND__ → simulates 404 (G4: patient-not-found banner)
// =============================================================================

function e2eFlag(name: string): boolean {
  try {
    return typeof localStorage !== 'undefined' && localStorage.getItem(name) === 'true';
  } catch {
    return false;
  }
}

@Injectable({ providedIn: 'root' })
export class TrainingSessionMockAdapter implements TrainingSessionPort {
  private sessionCounter = 0;

  start(request: StartSessionRequest): Observable<TrainingSession> {
    const delay = e2eFlag('__TREI_E2E_FAST__') ? 100 : 400;

    return timer(delay).pipe(
      switchMap(() => {
        // G4 (FE-025 / P7): simulate patient-not-found 404 for E2E validation
        if (e2eFlag('__TREI_E2E_PATIENT_NOT_FOUND__')) {
          return throwError(() =>
            Object.assign(new Error('E2E: paciente não encontrado'), { status: 404 }),
          );
        }

        this.sessionCounter++;
        const session: TrainingSession = {
          sessaoId: `s-mock-${this.sessionCounter}`,
          pacienteId: request.pacienteId,
          localeAutoria: request.localeAutoria,
          estado: 'ativa',
          llmModelId: 'claude-3-5-sonnet-20241022',
          iniciadaEm: new Date().toISOString(),
        };
        return of(session);
      }),
    );
  }
}
