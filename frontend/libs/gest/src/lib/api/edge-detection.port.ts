import { InjectionToken } from '@angular/core';
import { Observable } from 'rxjs';
import { ComandoDraft, EdgeRecognitionSignal } from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// EdgeDetectionPort — SPEC-04 §2 (GEST-FE-007)
// Wraps the localhost WS/SSE bridge to the local edge agent. GestureTestFacade
// is the only consumer; it decides real vs mock based on deviceOnline/degraded.
// ---------------------------------------------------------------------------

export interface EdgeDetectionPort {
  /** Open a real bridge session for the given draft. Transparently falls back
   * to an honest degraded stream (never a fake "recognized") if the bridge is
   * unreachable or the negotiated contractVersion is incompatible. */
  openStream(draft: ComandoDraft): Observable<EdgeRecognitionSignal>;

  /** Explicit degraded/simulated stream — used directly when deviceOnline is
   * already known false, or as the fallback path from openStream(). */
  mockStream(draft: ComandoDraft): Observable<EdgeRecognitionSignal>;

  /** Close the active bridge session, if any. Idempotent. */
  close(): void;
}

export const EDGE_DETECTION_PORT = new InjectionToken<EdgeDetectionPort>('EDGE_DETECTION_PORT');
