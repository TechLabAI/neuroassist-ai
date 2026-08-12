import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { ComandoDraft, EdgeRecognitionSignal } from '@neuroassist/shared/contracts';
import { EdgeDetectionPort } from './edge-detection.port';

const NEGOTIATED_VERSION = '1.0.0';

// Dev/test default (provideGest({ adapter: 'mock' })) — no backend, no edge
// agent required. Honest-degraded invariant (ADR-005): never emits
// signalKind:'recognized' — only a liveness ack followed by 'degraded', so the
// facade's own timeout finalizes the run as not-recognized rather than faking
// a positive result.
@Injectable()
export class EdgeDetectionMockAdapter implements EdgeDetectionPort {
  openStream(draft: ComandoDraft): Observable<EdgeRecognitionSignal> {
    return this.mockStream(draft);
  }

  mockStream(draft: ComandoDraft): Observable<EdgeRecognitionSignal> {
    return new Observable<EdgeRecognitionSignal>((subscriber) => {
      const ackTimer = setTimeout(() => {
        subscriber.next({
          contractVersion: NEGOTIATED_VERSION,
          signalKind: 'preview_frame_ack',
          commandDraftRef: draft.commandDraftRef,
          primitives: [],
          timingWindow: { startMs: 0, endMs: 0, durationMs: 0 },
          degraded: true,
          emittedAt: new Date().toISOString(),
        });
      }, 300);

      const degradedTimer = setTimeout(() => {
        subscriber.next({
          contractVersion: NEGOTIATED_VERSION,
          signalKind: 'degraded',
          commandDraftRef: draft.commandDraftRef,
          primitives: [],
          timingWindow: { startMs: 0, endMs: 0, durationMs: 0 },
          degraded: true,
          emittedAt: new Date().toISOString(),
        });
      }, 900);

      return () => {
        clearTimeout(ackTimer);
        clearTimeout(degradedTimer);
      };
    });
  }

  close(): void {
    // Stateless — subscriptions are torn down by the facade's unsubscribe.
  }
}
