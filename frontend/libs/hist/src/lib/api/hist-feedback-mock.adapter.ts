import { Injectable } from '@angular/core';
import { Observable, of, throwError, timer } from 'rxjs';
import { switchMap } from 'rxjs/operators';
import { HistFeedbackPort } from './hist-feedback.port';
import { FeedbackWriteDto, FeedbackResponse } from '../hist.types';

// =============================================================================
// HistFeedbackMockAdapter — dev/test (default adapter)
// Simulates POST /api/v1/evnt/events/{id}/feedback (evnt-service, SPEC-04 §2.4).
// Non-optimistic: caller reads verdict from the returned FeedbackResponse.
// Delay 600ms — slightly longer than list to model async feedback confirmation.
// 409 scenario: eventId already has a confirmed verdict → throwError({status:409}).
// =============================================================================

const _confirmedVerdicts = new Map<string, FeedbackResponse>();

@Injectable({ providedIn: 'root' })
export class HistFeedbackMockAdapter implements HistFeedbackPort {
  markFeedback(eventId: string, dto: FeedbackWriteDto): Observable<FeedbackResponse> {
    return timer(600).pipe(
      switchMap(() => {
        const existing = _confirmedVerdicts.get(eventId);
        if (existing) {
          // 409: already-terminal — return existing verdict so facade can reconcile
          return throwError(() =>
            Object.assign(new Error('Feedback already confirmed'), {
              status: 409,
              response: existing,
            }),
          );
        }
        const resp: FeedbackResponse = {
          eventId,
          verdict: dto.verdict,
          confirmedAt: new Date().toISOString(),
        };
        _confirmedVerdicts.set(eventId, resp);
        return of(resp);
      }),
    );
  }
}
