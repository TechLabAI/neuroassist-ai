import { Injectable } from '@angular/core';
import { Observable, interval, map, take } from 'rxjs';
import { EdgeDetectionPort } from './edge-detection.port';
import { EdgeRecognitionSignal, ComandoDraft } from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// EdgeDetectionMockAdapter — always emits degraded:true signals
// Used in dev (edgeLiveBridge:off) and as the fallback when the edge agent is
// unavailable. Follows the honest-degraded invariant: never emits recognized
// without degraded:true (ADR-005).
// ---------------------------------------------------------------------------

@Injectable()
export class EdgeDetectionMockAdapter implements EdgeDetectionPort {
  private readonly CONTRACT_VERSION = '1.0.0';

  openStream(draft: ComandoDraft): Observable<EdgeRecognitionSignal> {
    // Mock delegate: behave like mockStream (honest degraded)
    return this.mockStream(draft);
  }

  mockStream(draft: ComandoDraft): Observable<EdgeRecognitionSignal> {
    const startMs = Date.now();

    return interval(800).pipe(
      take(4),
      map((tick) => {
        const isSimulatedRecognition = tick === 3;
        return {
          contractVersion: this.CONTRACT_VERSION,
          signalKind: isSimulatedRecognition
            ? ('recognized' as const)
            : tick === 0
              ? ('preview_frame_ack' as const)
              : ('not_recognized' as const),
          commandDraftRef: draft.commandDraftRef,
          primitives: [
            { engineFeatureRef: draft.primitivoPrincipal, confidence: 0.0 }, // internal; never exposed
          ],
          timingWindow: {
            startMs,
            endMs: startMs + (tick + 1) * 800,
            durationMs: (tick + 1) * 800,
          },
          degraded: true, // INVARIANT: mock stream is ALWAYS degraded
          emittedAt: new Date().toISOString(),
        } satisfies EdgeRecognitionSignal;
      }),
    );
  }

  close(): void {
    // nothing to release — mock stream
  }
}
