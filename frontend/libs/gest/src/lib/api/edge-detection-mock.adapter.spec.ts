import { EdgeDetectionMockAdapter } from './edge-detection-mock.adapter';
import { ComandoDraft, EdgeRecognitionSignal } from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// EdgeDetectionMockAdapter — unit tests (GEST-FE-007, ADR-005)
// Honest-degraded invariant: never emits signalKind:'recognized'.
// ---------------------------------------------------------------------------

const simpleDraft: ComandoDraft = {
  commandDraftRef: 'mock-draft-001',
  rotulo: 'Água',
  primitivoPrincipal: 'piscar-lento',
  primitivoSecundario: null,
  sensibilidade: 'media',
  urgencia: 'rotina',
  tempoMinimoGestoMs: 500,
  tempoMaximoGestoMs: 3000,
  composite: false,
};

describe('EdgeDetectionMockAdapter', () => {
  let adapter: EdgeDetectionMockAdapter;

  beforeEach(() => {
    adapter = new EdgeDetectionMockAdapter();
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
  });

  describe('mockStream', () => {
    it('emits preview_frame_ack at 300ms with degraded:true', () => {
      const received: EdgeRecognitionSignal[] = [];
      adapter.mockStream(simpleDraft).subscribe((s) => received.push(s));

      jest.advanceTimersByTime(300);

      expect(received).toHaveLength(1);
      expect(received[0].signalKind).toBe('preview_frame_ack');
      expect(received[0].degraded).toBe(true);
      expect(received[0].commandDraftRef).toBe(simpleDraft.commandDraftRef);
      expect(received[0].contractVersion).toBe('1.0.0');
    });

    it('emits degraded signal at 900ms', () => {
      const received: EdgeRecognitionSignal[] = [];
      adapter.mockStream(simpleDraft).subscribe((s) => received.push(s));

      jest.advanceTimersByTime(900);

      expect(received).toHaveLength(2);
      expect(received[1].signalKind).toBe('degraded');
      expect(received[1].degraded).toBe(true);
    });

    it('emits exactly preview_frame_ack then degraded — no other signals', () => {
      const received: EdgeRecognitionSignal[] = [];
      adapter.mockStream(simpleDraft).subscribe((s) => received.push(s));

      jest.advanceTimersByTime(10_000);

      expect(received).toHaveLength(2);
      expect(received[0].signalKind).toBe('preview_frame_ack');
      expect(received[1].signalKind).toBe('degraded');
    });

    // -----------------------------------------------------------------------
    // CRÍTICO: honest-degraded invariant (ADR-005)
    // -----------------------------------------------------------------------
    it('[ADR-005] never emits signalKind:recognized — honest-degraded invariant', () => {
      const received: EdgeRecognitionSignal[] = [];
      adapter.mockStream(simpleDraft).subscribe((s) => received.push(s));

      jest.advanceTimersByTime(10_000);

      const hasRecognized = received.some((s) => s.signalKind === 'recognized');
      expect(hasRecognized).toBe(false);
    });

    it('clears timers on unsubscribe — no emission after teardown', () => {
      const received: EdgeRecognitionSignal[] = [];
      const sub = adapter.mockStream(simpleDraft).subscribe((s) => received.push(s));

      sub.unsubscribe();
      jest.advanceTimersByTime(900);

      expect(received).toHaveLength(0);
    });

    it('each subscriber gets an independent timer sequence', () => {
      const received1: EdgeRecognitionSignal[] = [];
      const received2: EdgeRecognitionSignal[] = [];

      adapter.mockStream(simpleDraft).subscribe((s) => received1.push(s));
      adapter.mockStream(simpleDraft).subscribe((s) => received2.push(s));

      jest.advanceTimersByTime(900);

      expect(received1).toHaveLength(2);
      expect(received2).toHaveLength(2);
    });
  });

  describe('openStream', () => {
    it('delegates to mockStream — emits same sequence', () => {
      const fromOpen: EdgeRecognitionSignal[] = [];
      const fromMock: EdgeRecognitionSignal[] = [];

      adapter.openStream(simpleDraft).subscribe((s) => fromOpen.push(s));
      adapter.mockStream(simpleDraft).subscribe((s) => fromMock.push(s));

      jest.advanceTimersByTime(900);

      expect(fromOpen).toHaveLength(2);
      expect(fromOpen[0].signalKind).toBe(fromMock[0].signalKind);
      expect(fromOpen[1].signalKind).toBe(fromMock[1].signalKind);
    });
  });

  describe('close', () => {
    it('is idempotent — does not throw when called multiple times', () => {
      expect(() => {
        adapter.close();
        adapter.close();
      }).not.toThrow();
    });
  });
});
