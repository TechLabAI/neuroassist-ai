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
//
// E2E flags (set via page.addInitScript → localStorage before Angular bootstraps):
//   __HIST_E2E_FEEDBACK_FAIL__ : markFeedback() throws a non-409 error → facade sets mark-error
//   __HIST_E2E_FEEDBACK_409__  : markFeedback() always throws 409 with verdict 'real'
//                                → facade reconciles and sets state to 'marked'
// =============================================================================

const _confirmedVerdicts = new Map<string, FeedbackResponse>();

function e2eFeedbackFlag(name: string): boolean {
  try {
    return typeof localStorage !== 'undefined' && localStorage.getItem(name) === 'true';
  } catch {
    return false;
  }
}

@Injectable({ providedIn: 'root' })
export class HistFeedbackMockAdapter implements HistFeedbackPort {
  markFeedback(eventId: string, dto: FeedbackWriteDto): Observable<FeedbackResponse> {
    return timer(600).pipe(
      switchMap(() => {
        // E2E: simulate non-409 write failure → facade sets mark-error state
        if (e2eFeedbackFlag('__HIST_E2E_FEEDBACK_FAIL__')) {
          return throwError(() =>
            Object.assign(new Error('E2E simulated feedback write failure'), { status: 500 }),
          );
        }

        // E2E: simulate 409 conflict → facade reconciles to existing verdict
        if (e2eFeedbackFlag('__HIST_E2E_FEEDBACK_409__')) {
          const reconciled: FeedbackResponse = {
            eventId,
            verdict: 'real',
            confirmedAt: new Date().toISOString(),
          };
          return throwError(() =>
            Object.assign(new Error('E2E simulated 409 conflict'), {
              status: 409,
              response: reconciled,
            }),
          );
        }

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
