import { InjectionToken, Signal } from '@angular/core';
import { Observable } from 'rxjs';
import { ComandoDraft, DetectorType, EdgeRecognitionSignal } from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// EdgeDetectionPort — SPEC-04 §2 (GEST-FE-007)
// Wraps the localhost WS/SSE bridge to the local edge agent. GestureTestFacade
// is the only consumer; it decides real vs mock based on deviceOnline/degraded.
// ---------------------------------------------------------------------------

export interface EdgeDetectionPort {
  /** Open a real detection session for the given draft. Transparently falls back
   * to an honest degraded stream (never a fake "recognized") if detection cannot
   * start.
   *
   * `source` is the MediaStream the facade already acquired. ADR-007 moved the
   * engine into the browser, so the detector needs the frames — and reopening the
   * camera would be wasteful (and fails on some devices). Adapters that detect
   * elsewhere (the legacy localhost bridge) ignore it. */
  openStream(draft: ComandoDraft, source?: MediaStream): Observable<EdgeRecognitionSignal>;

  /** Explicit degraded/simulated stream — used directly when deviceOnline is
   * already known false, or as the fallback path from openStream(). */
  mockStream(draft: ComandoDraft): Observable<EdgeRecognitionSignal>;

  /** Close the active bridge session, if any. Idempotent. */
  close(): void;

  /** Detector algorithm in use.
   * 'browser_mediapipe' = motor do produto desde a ADR-007 (MediaPipe no browser);
   * 'ear_bri' / 'onnx'  = agente Python legado, reportado no handshake WS (ADR-006);
   * null = ainda não iniciado, ou close() foi chamado (o caminho mock fica null). */
  readonly detectorType: Signal<DetectorType | null>;
}

export const EDGE_DETECTION_PORT = new InjectionToken<EdgeDetectionPort>('EDGE_DETECTION_PORT');
