import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import { StartSessionRequest, TrainingSession } from '../trei.types';

// =============================================================================
// TrainingSessionPort — §2.1 POST /api/v1/trei/sessoes
// Abstracts session lifecycle (start only in MVP; end is FE-side state only).
// Caregiver scope: treinamento.sessao.conduzir (SPEC-01).
// =============================================================================

export interface TrainingSessionPort {
  /**
   * POST /api/v1/trei/sessoes — start a new LLM authoring session.
   * 201 → TrainingSession; 404 → patient-not-found.
   */
  start(request: StartSessionRequest): Observable<TrainingSession>;
}

export const TRAINING_SESSION_PORT = new InjectionToken<TrainingSessionPort>(
  'TrainingSessionPort',
);
