import { TestBed } from '@angular/core/testing';
import { EdgeDetectionHttpAdapter } from './edge-detection-http.adapter';
import { EDGE_BRIDGE_WS_URL } from './gest-service.config';
import { ComandoDraft, EdgeRecognitionSignal } from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// MockWebSocket — synchronous test double for the browser WebSocket API.
// close() sets readyState=CLOSED without firing the 'close' listener
// synchronously to match the real WebSocket's asynchronous 'close' event
// (which fires after the connection is fully torn down, not inline with close()).
// Call simulateClose() explicitly when the test needs the 'close' listener to fire.
// ---------------------------------------------------------------------------

class MockWebSocket {
  static readonly CONNECTING = 0;
  static readonly OPEN = 1;
  static readonly CLOSING = 2;
  static readonly CLOSED = 3;
  static instances: MockWebSocket[] = [];

  readonly CONNECTING = 0;
  readonly OPEN = 1;
  readonly CLOSING = 2;
  readonly CLOSED = 3;

  readyState: number = MockWebSocket.CONNECTING;
  readonly sentMessages: string[] = [];
  private readonly listeners = new Map<string, ((ev: Event) => void)[]>();

  constructor(public readonly url: string) {
    MockWebSocket.instances.push(this);
  }

  addEventListener(event: string, cb: (ev: Event) => void): void {
    const list = this.listeners.get(event) ?? [];
    list.push(cb);
    this.listeners.set(event, list);
  }

  send(data: string): void {
    this.sentMessages.push(data);
  }

  /** Marks the socket closed without firing the 'close' listener inline —
   * matches real WebSocket semantics where 'close' is dispatched asynchronously. */
  close(): void {
    this.readyState = MockWebSocket.CLOSED;
  }

  // -----------------------------------------------------------------------
  // Simulation helpers
  // -----------------------------------------------------------------------

  simulateOpen(): void {
    this.readyState = MockWebSocket.OPEN;
    this._emit('open', new Event('open'));
  }

  simulateMessage(data: object): void {
    const ev = Object.assign(new Event('message'), {
      data: JSON.stringify(data),
    }) as MessageEvent;
    this._emit('message', ev);
  }

  simulateError(): void {
    this._emit('error', new Event('error'));
  }

  simulateClose(): void {
    this.readyState = MockWebSocket.CLOSED;
    this._emit('close', new Event('close'));
  }

  private _emit(event: string, ev: Event): void {
    (this.listeners.get(event) ?? []).forEach((cb) => cb(ev));
  }
}

// ---------------------------------------------------------------------------
// Test data
// ---------------------------------------------------------------------------

const simpleDraft: ComandoDraft = {
  commandDraftRef: 'http-draft-001',
  rotulo: 'Água',
  primitivoPrincipal: 'piscar-lento',
  primitivoSecundario: null,
  sensibilidade: 'media',
  urgencia: 'rotina',
  tempoMinimoGestoMs: 500,
  tempoMaximoGestoMs: 5000,
  composite: false,
};

const compositeDraft: ComandoDraft = {
  commandDraftRef: 'http-draft-002',
  rotulo: 'SOS',
  primitivoPrincipal: 'piscar-rapido',
  primitivoSecundario: 'desvio-olho',
  sensibilidade: 'alta',
  urgencia: 'emergencia',
  tempoMinimoGestoMs: 200,
  tempoMaximoGestoMs: 8000,
  composite: true,
};

function makeSignal(overrides: Partial<EdgeRecognitionSignal> = {}): EdgeRecognitionSignal {
  return {
    contractVersion: '1.0.0',
    signalKind: 'recognized',
    commandDraftRef: simpleDraft.commandDraftRef,
    primitives: [{ engineFeatureRef: 'piscar-lento', confidence: 0.9 }],
    timingWindow: { startMs: 0, endMs: 500, durationMs: 500 },
    degraded: false,
    emittedAt: new Date().toISOString(),
    ...overrides,
  };
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe('EdgeDetectionHttpAdapter', () => {
  let adapter: EdgeDetectionHttpAdapter;

  beforeEach(() => {
    jest.useFakeTimers();
    MockWebSocket.instances = [];
    (global as unknown as Record<string, unknown>)['WebSocket'] = MockWebSocket;

    TestBed.configureTestingModule({
      providers: [
        EdgeDetectionHttpAdapter,
        { provide: EDGE_BRIDGE_WS_URL, useValue: 'ws://localhost:8765/live-test' },
      ],
    });

    adapter = TestBed.inject(EdgeDetectionHttpAdapter);
  });

  afterEach(() => {
    jest.useRealTimers();
    TestBed.resetTestingModule();
  });

  describe('successful handshake (version match)', () => {
    it('connects to the bridge URL', () => {
      adapter.openStream(simpleDraft).subscribe();
      expect(MockWebSocket.instances[0].url).toBe('ws://localhost:8765/live-test');
    });

    it('sends start_session after receiving hello with matching contractVersion', () => {
      adapter.openStream(simpleDraft).subscribe();
      const ws = MockWebSocket.instances[0];

      ws.simulateMessage({ type: 'hello', contractVersion: '1.0.0' });

      expect(ws.sentMessages).toHaveLength(1);
      const msg = JSON.parse(ws.sentMessages[0]);
      expect(msg.type).toBe('start_session');
      expect(msg.commandDraftRef).toBe(simpleDraft.commandDraftRef);
      expect(msg.tipoGestoId).toBe(simpleDraft.primitivoPrincipal);
    });

    it('forwards signal messages to subscriber', () => {
      const received: EdgeRecognitionSignal[] = [];
      const sub = adapter.openStream(simpleDraft).subscribe((s) => received.push(s));

      const ws = MockWebSocket.instances[0];
      ws.simulateMessage({ type: 'hello', contractVersion: '1.0.0' });
      ws.simulateMessage({ type: 'signal', signal: makeSignal({ signalKind: 'recognized' }) });

      expect(received).toHaveLength(1);
      expect(received[0].signalKind).toBe('recognized');

      sub.unsubscribe();
    });

    it('completes subscriber when socket closes', () => {
      let completed = false;
      adapter.openStream(simpleDraft).subscribe({ complete: () => { completed = true; } });

      const ws = MockWebSocket.instances[0];
      ws.simulateClose();

      expect(completed).toBe(true);
    });
  });

  describe('composite command: sends advance message after step-1 recognized', () => {
    it('sends a second start_session for the secondary primitive', () => {
      adapter.openStream(compositeDraft).subscribe();
      const ws = MockWebSocket.instances[0];

      ws.simulateMessage({ type: 'hello', contractVersion: '1.0.0' });
      // step 1 recognized: primitives[0].engineFeatureRef matches primitivoPrincipal
      ws.simulateMessage({
        type: 'signal',
        signal: makeSignal({
          commandDraftRef: compositeDraft.commandDraftRef,
          signalKind: 'recognized',
          primitives: [{ engineFeatureRef: compositeDraft.primitivoPrincipal, confidence: 0.9 }],
        }),
      });

      expect(ws.sentMessages).toHaveLength(2);
      const advance = JSON.parse(ws.sentMessages[1]);
      expect(advance.type).toBe('start_session');
      expect(advance.tipoGestoId).toBe(compositeDraft.primitivoSecundario);
    });
  });

  describe('version mismatch fallback', () => {
    it('closes socket when bridge returns wrong contractVersion', () => {
      adapter.openStream(simpleDraft).subscribe();
      const ws = MockWebSocket.instances[0];

      ws.simulateMessage({ type: 'hello', contractVersion: '9.9.9' });

      expect(ws.readyState).toBe(MockWebSocket.CLOSED);
    });

    it('falls back to degraded stream on version mismatch (emits degraded signals)', () => {
      const received: EdgeRecognitionSignal[] = [];
      adapter.openStream(simpleDraft).subscribe((s) => received.push(s));

      const ws = MockWebSocket.instances[0];
      ws.simulateMessage({ type: 'hello', contractVersion: '9.9.9' });

      // Advance time so the fallback mock adapter emits its preview_frame_ack
      jest.advanceTimersByTime(300);

      expect(received.length).toBeGreaterThan(0);
      expect(received[0].degraded).toBe(true);
    });
  });

  describe('WebSocket error fallback', () => {
    it('falls back to degraded stream on WS error', () => {
      const received: EdgeRecognitionSignal[] = [];
      adapter.openStream(simpleDraft).subscribe((s) => received.push(s));

      const ws = MockWebSocket.instances[0];
      ws.simulateError();

      jest.advanceTimersByTime(300);

      expect(received.length).toBeGreaterThan(0);
      expect(received[0].degraded).toBe(true);
    });
  });

  describe('handshake timeout fallback', () => {
    it('falls back to degraded stream when hello not received within 2000ms', () => {
      const received: EdgeRecognitionSignal[] = [];
      adapter.openStream(simpleDraft).subscribe((s) => received.push(s));

      // Advance past HANDSHAKE_TIMEOUT_MS (2000ms)
      jest.advanceTimersByTime(2001);
      // Advance enough for the fallback mock adapter to emit its first signal
      jest.advanceTimersByTime(300);

      expect(received.length).toBeGreaterThan(0);
      expect(received[0].degraded).toBe(true);
    });
  });

  describe('close()', () => {
    it('closes the active socket', () => {
      adapter.openStream(simpleDraft).subscribe();
      const ws = MockWebSocket.instances[0];

      adapter.close();

      expect(ws.readyState).toBe(MockWebSocket.CLOSED);
    });

    it('is idempotent when called before any stream', () => {
      expect(() => adapter.close()).not.toThrow();
    });

    it('does not throw when called twice', () => {
      adapter.openStream(simpleDraft).subscribe();
      expect(() => {
        adapter.close();
        adapter.close();
      }).not.toThrow();
    });
  });

  // ---------------------------------------------------------------------------
  // ADR-006: detectorType handshake parsing
  // Verifies that the adapter reads the detectorType field from the 'hello'
  // message and exposes it as a signal — the facade + component use this to
  // decide whether to show the interim EAR/BRI banner.
  // ---------------------------------------------------------------------------

  describe('[ADR-006] detectorType handshake parsing', () => {
    it('detectorType is null before any handshake', () => {
      expect(adapter.detectorType()).toBeNull();
    });

    it('detectorType is "ear_bri" when hello includes detectorType:"ear_bri"', () => {
      adapter.openStream(simpleDraft).subscribe();
      const ws = MockWebSocket.instances[0];

      ws.simulateMessage({ type: 'hello', contractVersion: '1.0.0', detectorType: 'ear_bri' });

      expect(adapter.detectorType()).toBe('ear_bri');
    });

    it('detectorType is "onnx" when hello includes detectorType:"onnx"', () => {
      adapter.openStream(simpleDraft).subscribe();
      const ws = MockWebSocket.instances[0];

      ws.simulateMessage({ type: 'hello', contractVersion: '1.0.0', detectorType: 'onnx' });

      expect(adapter.detectorType()).toBe('onnx');
    });

    it('detectorType remains null when hello omits detectorType (legacy agent backward compat)', () => {
      adapter.openStream(simpleDraft).subscribe();
      const ws = MockWebSocket.instances[0];

      // Legacy hello message without detectorType field
      ws.simulateMessage({ type: 'hello', contractVersion: '1.0.0' });

      expect(adapter.detectorType()).toBeNull();
    });

    it('detectorType resets to null after close()', () => {
      adapter.openStream(simpleDraft).subscribe();
      const ws = MockWebSocket.instances[0];

      ws.simulateMessage({ type: 'hello', contractVersion: '1.0.0', detectorType: 'ear_bri' });
      expect(adapter.detectorType()).toBe('ear_bri');

      adapter.close();

      expect(adapter.detectorType()).toBeNull();
    });

    it('version mismatch fallback does not set detectorType (connection is closed before it could)', () => {
      adapter.openStream(simpleDraft).subscribe();
      const ws = MockWebSocket.instances[0];

      ws.simulateMessage({ type: 'hello', contractVersion: '9.9.9', detectorType: 'ear_bri' });

      // Adapter falls back on version mismatch — detectorType is NOT set because the branch
      // returns before reaching the detectorType assignment
      expect(adapter.detectorType()).toBeNull();
    });
  });
});
