import { Injectable, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { ComandoDraft, DetectorType, EdgeRecognitionSignal } from '@neuroassist/shared/contracts';
import { EdgeDetectionPort } from './edge-detection.port';

const NEGOTIATED_VERSION = '1.0.0';

// Dev/test default (provideGest({ adapter: 'mock' })) — no backend, no edge
// agent required. Honest-degraded invariant (ADR-005): never emits
// signalKind:'recognized' in the default path — only a liveness ack followed
// by 'degraded', so the facade's own timeout finalizes as not-recognized
// rather than faking a positive result.
//
// E2E test hooks (Playwright addInitScript only — never set in production):
//   window.__GEST_E2E_DETECTION = 'mock'
//     → provideGest() escolhe ESTE adapter em vez do motor do browser (ADR-007).
//       Sem esse flag os hooks abaixo são inertes na app: o adapter nem é
//       instanciado. Ver gest.providers.ts.
//   window.__GEST_E2E_MOCK_RECOGNIZED = true
//     → fast recognition path: ack (300ms) then recognized (600ms).
//       Composite drafts also get a second recognized at 2000ms.
//   window.__GEST_E2E_MOCK_STREAM_ERROR = true
//     → error path: observable throws at 400ms (tests facade error handler).
@Injectable()
export class EdgeDetectionMockAdapter implements EdgeDetectionPort {
  /** ADR-006: mock path does not represent the interim EAR/BRI detector — detectorType
   * remains null. The banner only appears on the real edge agent path (edgeLiveBridge:on). */
  readonly detectorType = signal<DetectorType | null>(null).asReadonly();

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
        // Composite: emit a second recognized at 2000ms so step 2 can complete.
        //
        // Eram 1200ms. O indicador de passo só existe enquanto o estado é
        // 'testing', então os 600ms entre o veredito do passo 1 e o final eram
        // toda a janela em que o E2E podia observar "passo 2 em negrito" — uma
        // corrida disfarçada de asserção. 1400ms de janela tira a temporização
        // da conta sem deixar o cenário lento.
        if (draft.composite) {
          timers.push(
            setTimeout(() => {
              subscriber.next({
                contractVersion: NEGOTIATED_VERSION,
                signalKind: 'recognized',
                commandDraftRef: draft.commandDraftRef,
                primitives: [],
                timingWindow: { startMs: 600, endMs: 2000, durationMs: 1400 },
                degraded: false,
                emittedAt: new Date().toISOString(),
              });
            }, 2000),
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

  /** Sem malha: este adaptador não tem landmarks — quem detecta é outro processo
   * (ou ninguém, no caso do mock). Ignorar em silêncio é o comportamento certo;
   * o componente não precisa saber qual motor está ligado para montar o canvas. */
  attachOverlay(): void {
    // no-op
  }
}
