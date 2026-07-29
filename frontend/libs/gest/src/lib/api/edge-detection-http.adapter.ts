import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, throwError, webSocket } from 'rxjs';
import { catchError, filter, switchMap, map } from 'rxjs';
import { EdgeDetectionPort, EDGE_DETECTION_PORT } from './edge-detection.port';
import { EdgeDetectionMockAdapter } from './edge-detection-mock.adapter';
import { EdgeRecognitionSignal, ComandoDraft } from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// EdgeDetectionHttpAdapter — real implementation (stub compilable for Layer 1).
// Connects to the same-device edge agent via WS/SSE (ADR-005, localhost bridge).
//
// TODO[GEST-FE-001]: implement real WS/SSE transport
// TODO[GEST-INT-004]: bridge transport auth (JWT-device vs mTLS) — OPEN decision
//   with EDGE team; isolate auth behind BRIDGE_TOKEN provider in gest.providers.ts
// ---------------------------------------------------------------------------

/** Advertised contractVersion from the edge agent bootstrap call.
 * Negotiated on openStream; cached per session. */
const EXPECTED_CONTRACT_VERSION = '1.0.0';

/** Base URL for the edge agent localhost bridge (configurable via environment). */
const EDGE_AGENT_BASE = 'localhost:8765'; // TODO[GEST-FE-001]: move to environment config

@Injectable()
export class EdgeDetectionHttpAdapter implements EdgeDetectionPort {
  private readonly http = inject(HttpClient);
  private readonly fallback = new EdgeDetectionMockAdapter();

  openStream(draft: ComandoDraft): Observable<EdgeRecognitionSignal> {
    // TODO[GEST-FE-001]: implement real WS connection to ws://EDGE_AGENT_BASE/stream
    // 1. GET /api/v1/gest/detection-contract to negotiate contractVersion
    // 2. If contractVersion !== EXPECTED_CONTRACT_VERSION, fall to mockStream (version-mismatch)
    // 3. Open WebSocket or EventSource to ws://EDGE_AGENT_BASE/stream?draftRef=...
    // 4. Parse incoming messages as EdgeRecognitionSignal
    // 5. Validate degraded invariant: if degraded:true on recognized, re-map signalKind

    // Stub: fall to mock until GEST-FE-001 is implemented
    return this.fallback.mockStream(draft);
  }

  mockStream(draft: ComandoDraft): Observable<EdgeRecognitionSignal> {
    return this.fallback.mockStream(draft);
  }

  close(): void {
    // TODO[GEST-FE-001]: close WS/SSE connection
    this.fallback.close();
  }
}
