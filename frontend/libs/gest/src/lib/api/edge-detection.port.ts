import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import { EdgeRecognitionSignal, ComandoDraft } from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// EdgeDetectionPort — SPEC-04 §2
// Abstraction over the localhost WS/SSE bridge to the same-device edge agent.
// The browser never owns the classifier; it streams a result (ADR-005).
// ---------------------------------------------------------------------------

export interface EdgeDetectionPort {
  /** Open a real stream to the edge agent for the given command draft.
   * Negotiates contractVersion on channel open.
   * IF the agent advertises an incompatible contractVersion, switches transparently
   * to mockStream (degraded, reason 'version-mismatch'). */
  openStream(draft: ComandoDraft): Observable<EdgeRecognitionSignal>;

  /** Fallback stream emitting degraded:true signals for every tick.
   * Used when the edge agent is offline, unreachable, or on a version mismatch.
   * Never emits a real recognized verdict — honest degraded invariant (ADR-005). */
  mockStream(draft: ComandoDraft): Observable<EdgeRecognitionSignal>;

  /** Unsubscribe and release the bridge connection. */
  close(): void;
}

export const EDGE_DETECTION_PORT = new InjectionToken<EdgeDetectionPort>(
  'EdgeDetectionPort',
);
