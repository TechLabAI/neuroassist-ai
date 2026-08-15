import { Injectable, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { ComandoDraft, EdgeRecognitionSignal } from '@neuroassist/shared/contracts';
import { EdgeDetectionPort } from './edge-detection.port';

const NEGOTIATED_VERSION = '1.0.0';

// Dev/test default (provideGest({ adapter: 'mock' })) — no backend, no edge
// agent required. Honest-degraded invariant (ADR-005): never emits
// signalKind:'recognized' in the default path — only a liveness ack followed
// by 'degraded', so the facade's own timeout finalizes as not-recognized
// rather than faking a positive result.
//
// E2E test hooks (Playwright addInitScript only — never set in production):
//   window.__GEST_E2E_MOCK_RECOGNIZED = true
//     → fast recognition path: ack (300ms) then recognized (600ms).
//       Composite drafts also get a second recognized at 1200ms.
//   window.__GEST_E2E_MOCK_STREAM_ERROR = true
//     → error path: observable throws at 400ms (tests facade error handler).
@Injectable()
export class EdgeDetectionMockAdapter implements EdgeDetectionPort {
  /** ADR-006: mock path does not represent the interim EAR/BRI detector — detectorType
   * remains null. The banner only appears on the real edge agent path (edgeLiveBridge:on). */
  readonly detectorType = signal<'ear_bri' | 'onnx' | null>(null).asReadonly();

  openStream(draft: ComandoDraft): Observable<EdgeRecognitionSignal> {
    return this.mockStream(draft);
  }

  mockStream(draft: ComandoDraft): Observable<EdgeRecognitionSignal> {
    return new Observable<EdgeRecognitionSignal>((subscriber) => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const w = typeof window !== 'undefined' ? (window as any) : null;
      const mockRecognized: boolean = w?.__GEST_E2E_MOCK_RECOGNIZED ?? false;
      const mockError: boolean = w?.__GEST_E2E_MOCK_STREAM_ERROR ?? false;
      const timers: ReturnType<typeof setTimeout>[] = [];

      if (mockError) {
        // E2E error path: simulate stream connection failure after 400ms.
        timers.push(
          setTimeout(() => {
            subscriber.error(new Error('[E2E] mock stream error (__GEST_E2E_MOCK_STREAM_ERROR)'));
          }, 400),
        );
      } else if (mockRecognized) {
        // E2E fast recognition path — bypasses honest-degraded sequence.
        // Use only in specs that explicitly test the recognition flow.
        timers.push(
          setTimeout(() => {
            subscriber.next({
              contractVersion: NEGOTIATED_VERSION,
              signalKind: 'preview_frame_ack',
              commandDraftRef: draft.commandDraftRef,
              primitives: [],
              timingWindow: { startMs: 0, endMs: 0, durationMs: 0 },
              degraded: false,
              emittedAt: new Date().toISOString(),
            });
          }, 300),
        );
        timers.push(
          setTimeout(() => {
            subscriber.next({
              contractVersion: NEGOTIATED_VERSION,
              signalKind: 'recognized',
              commandDraftRef: draft.commandDraftRef,
              primitives: [],
              timingWindow: { startMs: 0, endMs: 600, durationMs: 600 },
              degraded: false,
              emittedAt: new Date().toISOString(),
            });
          }, 600),
        );
        // Composite: emit a second recognized at 1200ms so step 2 can complete.
        if (draft.composite) {
          timers.push(
            setTimeout(() => {
              subscriber.next({
                contractVersion: NEGOTIATED_VERSION,
                signalKind: 'recognized',
                commandDraftRef: draft.commandDraftRef,
                primitives: [],
                timingWindow: { startMs: 600, endMs: 1200, durationMs: 600 },
                degraded: false,
                emittedAt: new Date().toISOString(),
              });
            }, 1200),
          );
        }
      } else {
        // Default honest-degraded path (ADR-005): ack then degraded signal.
        // Facade timeout finalizes as not-recognized — never a fake positive.
        timers.push(
          setTimeout(() => {
            subscriber.next({
              contractVersion: NEGOTIATED_VERSION,
              signalKind: 'preview_frame_ack',
              commandDraftRef: draft.commandDraftRef,
              primitives: [],
              timingWindow: { startMs: 0, endMs: 0, durationMs: 0 },
              degraded: true,
              emittedAt: new Date().toISOString(),
            });
          }, 300),
        );
        timers.push(
          setTimeout(() => {
            subscriber.next({
              contractVersion: NEGOTIATED_VERSION,
              signalKind: 'degraded',
              commandDraftRef: draft.commandDraftRef,
              primitives: [],
              timingWindow: { startMs: 0, endMs: 0, durationMs: 0 },
              degraded: true,
              emittedAt: new Date().toISOString(),
            });
          }, 900),
        );
      }

      return () => {
        timers.forEach(clearTimeout);
      };
    });
  }

  close(): void {
    // Stateless — subscriptions are torn down by the facade's unsubscribe.
  }
}
