import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { HistFeedbackPort } from './hist-feedback.port';
import { FeedbackWriteDto, FeedbackResponse } from '../hist.types';

// =============================================================================
// HistFeedbackHttpAdapter — production adapter (stub, compilable)
// Target: POST /api/v1/evnt/events/{eventId}/feedback (evnt-service via GWY).
// feedbackTarget flag: if G1 resolves via hist-service proxy, the URL changes
// but the DTO stays identical (SPEC-04 §2.4).
// TODO[INT-FE-HIST-005]: activate once evnt-service + GWY route is deployed.
// =============================================================================

@Injectable({ providedIn: 'root' })
export class HistFeedbackHttpAdapter implements HistFeedbackPort {
  private readonly http = inject(HttpClient);

  markFeedback(eventId: string, dto: FeedbackWriteDto): Observable<FeedbackResponse> {
    // Base path targets evnt-service (feedbackTarget='evnt', SPEC-04 §2.4 / G1).
    return this.http.post<FeedbackResponse>(
      `/api/v1/evnt/events/${eventId}/feedback`,
      dto,
    );
  }
}
