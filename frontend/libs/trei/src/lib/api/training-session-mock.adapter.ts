import { Injectable } from '@angular/core';
import { Observable, of, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { TrainingSessionPort } from './training-session.port';
import { StartSessionRequest, TrainingSession } from '../trei.types';

// =============================================================================
// TrainingSessionMockAdapter — dev/test (default adapter)
// Simulates POST /api/v1/trei/sessoes with 400ms delay (project standard).
// Seed: Márcio Amaral (pac-marcio) — Família Amaral tenant.
// =============================================================================

@Injectable({ providedIn: 'root' })
export class TrainingSessionMockAdapter implements TrainingSessionPort {
  private sessionCounter = 0;

  start(request: StartSessionRequest): Observable<TrainingSession> {
    return timer(400).pipe(
      switchMap(() => {
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
