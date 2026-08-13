import { Injectable, inject, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { ComandoDraft, EdgeRecognitionSignal } from '@neuroassist/shared/contracts';
import { EdgeDetectionPort } from './edge-detection.port';
import { EdgeDetectionMockAdapter } from './edge-detection-mock.adapter';
import { EDGE_BRIDGE_WS_URL } from './gest-service.config';

const NEGOTIATED_VERSION = '1.0.0';
const HANDSHAKE_TIMEOUT_MS = 2000;

/**
 * Wire messages exchanged with the local edge agent (backend/edge-agent-ws.py).
 * Localhost-only channel — never routed through GWY. Auth is provisional
 * (GEST-INT-004 / INT-GEST-007 track the final mTLS-vs-OAuth2 decision); this
 * client sends an opaque deviceId-shaped token and does not enforce a scheme.
 */
interface HelloMessage {
  type: 'hello';
  contractVersion: string;
  /** ADR-006: interim detector type included since handshake protocol v1.
   * 'ear_bri' = EAR/BRI threshold detector (interim); 'onnx' = production classifier.
   * Optional for backward compatibility with legacy agents that predate ADR-006. */
  detectorType?: 'ear_bri' | 'onnx';
}
interface StartSessionMessage {
  type: 'start_session';
  commandDraftRef: string;
  tipoGestoId: string;
}
interface SignalMessage {
  type: 'signal';
  signal: EdgeRecognitionSignal;
}
type InboundMessage = HelloMessage | SignalMessage;

// Real adapter (provideGest({ adapter: 'http' })) — GEST-FE-007. Opens a
// localhost WS session to the edge agent, negotiates contractVersion, and
// forwards EdgeRecognitionSignal frames verbatim. Falls back to the honest
// mock stream (never a fake "recognized") whenever the bridge is unreachable
// or the negotiated version doesn't match.
@Injectable()
export class EdgeDetectionHttpAdapter implements EdgeDetectionPort {
  private readonly bridgeUrl = inject(EDGE_BRIDGE_WS_URL);
  private readonly fallback = new EdgeDetectionMockAdapter();
  private activeSocket: WebSocket | null = null;

  private readonly _detectorType = signal<'ear_bri' | 'onnx' | null>(null);
  /** ADR-006: current detector type as reported in the WS handshake. null before
   * handshake is received or after close(). Drives the interim banner in the component. */
  readonly detectorType = this._detectorType.asReadonly();

  openStream(draft: ComandoDraft): Observable<EdgeRecognitionSignal> {
    return new Observable<EdgeRecognitionSignal>((subscriber) => {
      let settled = false;
      let socket: WebSocket;
      try {
        socket = new WebSocket(this.bridgeUrl);
      } catch {
        this.forwardDegraded(subscriber, draft);
        return;
      }
      this.activeSocket = socket;

      const handshakeTimer = setTimeout(() => {
        if (!settled) {
          settled = true;
          socket.close();
          this.forwardDegraded(subscriber, draft);
        }
      }, HANDSHAKE_TIMEOUT_MS);

      socket.addEventListener('open', () => {
        // Handshake happens on the server's "hello" message, not on open.
      });

      socket.addEventListener('message', (ev: MessageEvent) => {
        let msg: InboundMessage;
        try {
          msg = JSON.parse(ev.data as string) as InboundMessage;
        } catch {
          return;
        }

        if (msg.type === 'hello') {
          clearTimeout(handshakeTimer);
          if (msg.contractVersion !== NEGOTIATED_VERSION) {
            settled = true;
            socket.close();
            this.forwardDegraded(subscriber, draft);
            return;
          }
          // ADR-006: capture detectorType from handshake to drive the interim banner.
          // Optional field — legacy agents that predate ADR-006 omit it (null stays).
          if (msg.detectorType) {
            this._detectorType.set(msg.detectorType);
          }
          settled = true;
          const startMsg: StartSessionMessage = {
            type: 'start_session',
            commandDraftRef: draft.commandDraftRef,
            tipoGestoId: draft.primitivoPrincipal,
          };
          socket.send(JSON.stringify(startMsg));
          return;
        }

        if (msg.type === 'signal') {
          const sig = msg.signal;
          subscriber.next(sig);

          // Composite step 1 done — tell the agent to watch the second primitive.
          if (
            sig.signalKind === 'recognized' &&
            draft.composite &&
            draft.primitivoSecundario &&
            sig.primitives[0]?.engineFeatureRef === draft.primitivoPrincipal
          ) {
            const advance: StartSessionMessage = {
              type: 'start_session',
              commandDraftRef: draft.commandDraftRef,
              tipoGestoId: draft.primitivoSecundario,
            };
            socket.send(JSON.stringify(advance));
          }
        }
      });

      socket.addEventListener('error', () => {
        clearTimeout(handshakeTimer);
        if (!settled) {
          settled = true;
          this.forwardDegraded(subscriber, draft);
        }
      });

      socket.addEventListener('close', () => {
        clearTimeout(handshakeTimer);
        if (this.activeSocket === socket) this.activeSocket = null;
        subscriber.complete();
      });

      return () => {
        clearTimeout(handshakeTimer);
        if (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING) {
          socket.close();
        }
        if (this.activeSocket === socket) this.activeSocket = null;
      };
    });
  }

  mockStream(draft: ComandoDraft): Observable<EdgeRecognitionSignal> {
    return this.fallback.mockStream(draft);
  }

  close(): void {
    this.activeSocket?.close();
    this.activeSocket = null;
    this._detectorType.set(null); // ADR-006: reset on close so the banner clears next session
  }

  /** Honest fallback: emit a single degraded signal and complete — never a
   * fake "recognized" (ADR-005). Mirrors mockStream's shape via the mock
   * adapter so both paths stay in sync. */
  private forwardDegraded(
    subscriber: { next: (v: EdgeRecognitionSignal) => void; complete: () => void },
    draft: ComandoDraft,
  ): void {
    this.mockStream(draft).subscribe({
      next: (v) => subscriber.next(v),
      complete: () => subscriber.complete(),
    });
  }
}
