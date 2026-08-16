import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import { FeedbackWriteDto, FeedbackResponse } from '../hist.types';

// =============================================================================
// HistFeedbackPort — SPEC-04 §2.4 (feedback write — evnt-service target)
// Separate from HistClientPort because the endpoint lives in evnt-service
// (POST /api/v1/evnt/events/{id}/feedback) per FE ADR / G1 resolution.
//
// `feedbackTarget` flag switches target to hist-service proxy if G1 resolves
// that way in execution phase; the DTO shape is identical either way.
//
// Non-optimistic: verdict is set only after the backend confirms (SPEC-03 §4).
// 409 → item already has a terminal verdict; facade reconciles to backend value.
// =============================================================================

export interface HistFeedbackPort {
  /**
   * POST /api/v1/evnt/events/{eventId}/feedback
   * `{ verdict: 'real' | 'falso_positivo' }`
   * Non-optimistic — caller sets item state only on success (SPEC-03 §4).
   */
  markFeedback(eventId: string, dto: FeedbackWriteDto): Observable<FeedbackResponse>;
}

export const HIST_FEEDBACK_PORT = new InjectionToken<HistFeedbackPort>('HistFeedbackPort');
