import { TestBed } from '@angular/core/testing';
import { Subject, EMPTY } from 'rxjs';
import { signal, WritableSignal } from '@angular/core';
import { GestureTestFacade } from './gest.facade';
import { EDGE_DETECTION_PORT, EdgeDetectionPort } from './api/edge-detection.port';
import {
  ComandoDraft,
  EdgeRecognitionSignal,
} from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// GestureTestFacade — unit tests (GEST-FE-017)
// Covers: state machine transitions, signal→VM mappings (7 cases), camera
// lifecycle, composite two-step progression, and honest-degraded invariant.
// ---------------------------------------------------------------------------

// ---------------------------------------------------------------------------
// Test data helpers
// ---------------------------------------------------------------------------

const simpleDraft: ComandoDraft = {
  commandDraftRef: 'facade-simple-001',
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
  commandDraftRef: 'facade-composite-002',
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
    signalKind: 'preview_frame_ack',
    commandDraftRef: simpleDraft.commandDraftRef,
    primitives: [],
    timingWindow: { startMs: 0, endMs: 0, durationMs: 0 },
    degraded: false,
    emittedAt: new Date().toISOString(),
    ...overrides,
  };
}

// Flush the camera-acquisition microtask chain:
// acquireCamera() uses `await getUserMedia` (one microtask) then the .then()
// in startTest calls subscribeToStream (second microtask). Two flushes ensure
// the subscription is active before we emit signals to the Subject.
async function flushCameraChain(): Promise<void> {
  await Promise.resolve(); // getUserMedia continuation
  await Promise.resolve(); // startTest .then handler → subscribeToStream
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------


function ackSignal(commandDraftRef: string): EdgeRecognitionSignal {
  return {
    contractVersion: '1.0.0',
    signalKind: 'preview_frame_ack',
    commandDraftRef,
    primitives: [],
    timingWindow: { startMs: 0, endMs: 0, durationMs: 0 },
    degraded: false,
    emittedAt: new Date(0).toISOString(),
  };
}

describe('GestureTestFacade', () => {
  let facade: GestureTestFacade;
  let mockPort: jest.Mocked<EdgeDetectionPort>;
  let signalSource: Subject<EdgeRecognitionSignal>;
  let detectorTypeSignal: WritableSignal<'ear_bri' | 'onnx' | null>;

  // Mock MediaStream track
  const mockTrack = { stop: jest.fn() };
  const mockMediaStream = { getTracks: () => [mockTrack] };

  beforeEach(() => {
    jest.useFakeTimers();

    signalSource = new Subject<EdgeRecognitionSignal>();
    detectorTypeSignal = signal<'ear_bri' | 'onnx' | null>(null);
    mockPort = {
      openStream: jest.fn().mockReturnValue(signalSource.asObservable()),
      mockStream: jest.fn().mockReturnValue(EMPTY),
      close: jest.fn(),
      detectorType: detectorTypeSignal.asReadonly(),
    };

    // Mock navigator.mediaDevices for the full acquisition path
    Object.defineProperty(global.navigator, 'mediaDevices', {
      value: {
        getUserMedia: jest.fn().mockResolvedValue(mockMediaStream),
      },
      configurable: true,
    });

    TestBed.configureTestingModule({
      providers: [
        GestureTestFacade,
        { provide: EDGE_DETECTION_PORT, useValue: mockPort },
      ],
    });

    facade = TestBed.inject(GestureTestFacade);
  });

  afterEach(() => {
    signalSource.complete();
    jest.useRealTimers();
    TestBed.resetTestingModule();
  });

  // -------------------------------------------------------------------------
  // Initial state
  // -------------------------------------------------------------------------

  describe('initial state', () => {
    it('starts with state:idle', () => {
      expect(facade.state().state).toBe('idle');
    });

    it('starts with degraded:false and deviceOnline:true (optimistic)', () => {
      expect(facade.state().degraded).toBe(false);
      expect(facade.state().deviceOnline).toBe(true);
    });

    it('starts with step:null', () => {
      expect(facade.state().step).toBeNull();
    });

    it('lastOutcome is null before any test run', () => {
      expect(facade.lastOutcome()).toBeNull();
    });

    it('exposes previewStream as null before camera acquired', () => {
      expect(facade.previewStream()).toBeNull();
    });
  });

  // -------------------------------------------------------------------------
  // setDeviceOnline — seeds the facade before the live-test mounts
  // -------------------------------------------------------------------------

  describe('setDeviceOnline', () => {
    it('setDeviceOnline(false) sets degraded:true and deviceOnline:false', () => {
      facade.setDeviceOnline(false);
      expect(facade.state().degraded).toBe(true);
      expect(facade.state().deviceOnline).toBe(false);
    });

    it('setDeviceOnline(false) uses device-offline as default reason', () => {
      facade.setDeviceOnline(false);
      expect(facade.state().degradedReason).toBe('device-offline');
    });

    it('setDeviceOnline(false, bridge-unreachable) sets custom reason', () => {
      facade.setDeviceOnline(false, 'bridge-unreachable');
      expect(facade.state().degradedReason).toBe('bridge-unreachable');
    });

    it('setDeviceOnline(true) clears degraded and degradedReason', () => {
      facade.setDeviceOnline(false);
      facade.setDeviceOnline(true);
      const vm = facade.state();
      expect(vm.degraded).toBe(false);
      expect(vm.deviceOnline).toBe(true);
      expect(vm.degradedReason).toBeNull();
    });
  });

  // -------------------------------------------------------------------------
  // startTest — synchronous state transitions
  // -------------------------------------------------------------------------

  describe('startTest — synchronous state transitions', () => {
    it('immediately transitions to state:testing', () => {
      facade.startTest(simpleDraft);
      expect(facade.state().state).toBe('testing');
    });

    it('sets commandDraftRef and rotulo from draft', () => {
      facade.startTest(simpleDraft);
      expect(facade.state().commandDraftRef).toBe(simpleDraft.commandDraftRef);
      expect(facade.state().rotulo).toBe(simpleDraft.rotulo);
    });

    it('sets step:null for a simple (non-composite) command', () => {
      facade.startTest(simpleDraft);
      expect(facade.state().step).toBeNull();
    });

    it('sets step:principal for a composite command', () => {
      facade.startTest(compositeDraft);
      expect(facade.state().step).toBe('principal');
    });

    it('preserves deviceOnline and degraded from previous state', () => {
      facade.setDeviceOnline(false);
      facade.startTest(simpleDraft);
      expect(facade.state().degraded).toBe(true);
    });
  });

  // -------------------------------------------------------------------------
  // stopTest
  // -------------------------------------------------------------------------

  describe('stopTest', () => {
    it('transitions from testing to idle', () => {
      facade.startTest(simpleDraft);
      expect(facade.state().state).toBe('testing');

      facade.stopTest();
      expect(facade.state().state).toBe('idle');
    });

    it('calls port.close()', () => {
      facade.startTest(simpleDraft);
      facade.stopTest();
      expect(mockPort.close).toHaveBeenCalled();
    });

    it('resets step to null', () => {
      facade.startTest(compositeDraft);
      facade.stopTest();
      expect(facade.state().step).toBeNull();
    });

    it('is safe to call when idle (no-op)', () => {
      expect(() => facade.stopTest()).not.toThrow();
    });
  });

  // -------------------------------------------------------------------------
  // retry
  // -------------------------------------------------------------------------

  describe('retry', () => {
    it('restarts the test with the same draft after stopTest', () => {
      facade.startTest(simpleDraft);
      facade.stopTest();

      expect(facade.state().state).toBe('idle');

      facade.retry();
      expect(facade.state().state).toBe('testing');
      expect(facade.state().commandDraftRef).toBe(simpleDraft.commandDraftRef);
    });

    it('does not throw when called before any startTest', () => {
      expect(() => facade.retry()).not.toThrow();
    });
  });

  // -------------------------------------------------------------------------
  // Signal handling — simple command (7 signal→VM mappings from SPEC-04 §3)
  // -------------------------------------------------------------------------

  describe('signal handling — simple command', () => {
    beforeEach(async () => {
      facade.startTest(simpleDraft);
      await flushCameraChain();
    });

    it('preview_frame_ack: stays in testing and updates contractVersion', () => {
      signalSource.next(
        makeSignal({ signalKind: 'preview_frame_ack', contractVersion: '1.0.0' }),
      );
      expect(facade.state().state).toBe('testing');
      expect(facade.state().contractVersion).toBe('1.0.0');
    });

    it('preview_frame_ack: propagates degraded flag from signal', () => {
      signalSource.next(
        makeSignal({ signalKind: 'preview_frame_ack', degraded: true }),
      );
      expect(facade.state().degraded).toBe(true);
    });

    it('recognized: finalizes with state:recognized', () => {
      signalSource.next(makeSignal({ signalKind: 'recognized', degraded: false }));
      expect(facade.state().state).toBe('recognized');
    });

    it('recognized: sets lastOutcome with recognized:true and degraded:false', () => {
      signalSource.next(makeSignal({ signalKind: 'recognized', degraded: false }));
      const outcome = facade.lastOutcome();
      expect(outcome?.recognized).toBe(true);
      expect(outcome?.degraded).toBe(false);
    });

    it('recognized: releases camera after finalizing', () => {
      signalSource.next(makeSignal({ signalKind: 'recognized', degraded: false }));
      expect(facade.previewStream()).toBeNull();
    });

    it('not_recognized: finalizes with state:not-recognized', () => {
      signalSource.next(makeSignal({ signalKind: 'not_recognized' }));
      expect(facade.state().state).toBe('not-recognized');
    });

    it('not_recognized: sets lastOutcome with recognized:false', () => {
      signalSource.next(makeSignal({ signalKind: 'not_recognized' }));
      expect(facade.lastOutcome()?.recognized).toBe(false);
    });

    it('degraded: updates vm.degraded and degradedReason to bridge-unreachable', () => {
      signalSource.next(makeSignal({ signalKind: 'degraded', degraded: true }));
      expect(facade.state().degraded).toBe(true);
      expect(facade.state().degradedReason).toBe('bridge-unreachable');
    });

    it('stream error: transitions to not-recognized', () => {
      signalSource.error(new Error('stream error'));
      expect(facade.state().state).toBe('not-recognized');
    });
  });

  // -------------------------------------------------------------------------
  // Signal handling — composite command (2-step progression)
  // -------------------------------------------------------------------------

  describe('signal handling — composite command (2-step)', () => {
    beforeEach(async () => {
      mockPort.openStream.mockReturnValue(signalSource.asObservable());
      facade.startTest(compositeDraft);
      await flushCameraChain();
    });

    it('recognized on step:principal advances to step:secundario, stays testing', () => {
      signalSource.next(
        makeSignal({
          commandDraftRef: compositeDraft.commandDraftRef,
          signalKind: 'recognized',
          degraded: false,
        }),
      );
      expect(facade.state().step).toBe('secundario');
      expect(facade.state().state).toBe('testing');
    });

    it('recognized on step:secundario finalizes as state:recognized', () => {
      // Step 1
      signalSource.next(
        makeSignal({
          commandDraftRef: compositeDraft.commandDraftRef,
          signalKind: 'recognized',
        }),
      );
      expect(facade.state().step).toBe('secundario');

      // Step 2
      signalSource.next(
        makeSignal({
          commandDraftRef: compositeDraft.commandDraftRef,
          signalKind: 'recognized',
        }),
      );
      expect(facade.state().state).toBe('recognized');
    });

    it('lastOutcome.composite is true for composite command', () => {
      signalSource.next(makeSignal({ signalKind: 'recognized' })); // step 1
      signalSource.next(makeSignal({ signalKind: 'recognized' })); // step 2
      expect(facade.lastOutcome()?.composite).toBe(true);
    });
  });

  // -------------------------------------------------------------------------
  // Honest-degraded invariant (ADR-005)
  // -------------------------------------------------------------------------

  describe('[ADR-005] honest-degraded invariant', () => {
    it('outcome.degraded is true when vm.degraded was true at finalization — even if signal says degraded:false', async () => {
      // Simulate device offline → degraded:true before starting the test
      facade.setDeviceOnline(false);

      // In degraded mode, mockStream is used (not openStream)
      const degradedSource = new Subject<EdgeRecognitionSignal>();
      mockPort.mockStream.mockReturnValue(degradedSource.asObservable());

      facade.startTest(simpleDraft);
      await flushCameraChain();

      // Emit recognized with degraded:false from signal — but vm was degraded
      degradedSource.next(makeSignal({ signalKind: 'recognized', degraded: false }));

      // The invariant: outcome.degraded must reflect the actual state (true)
      expect(facade.lastOutcome()?.degraded).toBe(true);
    });

    it('outcome.degraded is false on clean path with non-degraded signal', async () => {
      facade.startTest(simpleDraft);
      await flushCameraChain();

      signalSource.next(makeSignal({ signalKind: 'recognized', degraded: false }));

      expect(facade.lastOutcome()?.degraded).toBe(false);
      expect(facade.lastOutcome()?.recognized).toBe(true);
    });
  });

  // -------------------------------------------------------------------------
  // Camera unavailable path
  // -------------------------------------------------------------------------

  describe('camera unavailable', () => {
    it('transitions to camera-error when mediaDevices is not available', async () => {
      // Remove mediaDevices to simulate environment without camera API
      Object.defineProperty(global.navigator, 'mediaDevices', {
        value: undefined,
        configurable: true,
      });

      facade.startTest(simpleDraft);
      await flushCameraChain();

      expect(facade.state().state).toBe('camera-error');
    });

    it('transitions to camera-error when getUserMedia rejects', async () => {
      Object.defineProperty(global.navigator, 'mediaDevices', {
        value: {
          getUserMedia: jest.fn().mockRejectedValue(new Error('Permission denied')),
        },
        configurable: true,
      });

      facade.startTest(simpleDraft);
      await flushCameraChain();

      expect(facade.state().state).toBe('camera-error');
    });
  });

  // -------------------------------------------------------------------------
  // Shortcut signals (computed)
  // -------------------------------------------------------------------------

  describe('computed shortcut signals', () => {
    it('testState reflects state()', () => {
      facade.startTest(simpleDraft);
      expect(facade.testState()).toBe('testing');
    });

    it('degraded computed reflects state().degraded', () => {
      facade.setDeviceOnline(false);
      expect(facade.degraded()).toBe(true);
    });

    it('deviceOnline computed reflects state().deviceOnline', () => {
      expect(facade.deviceOnline()).toBe(true);
      facade.setDeviceOnline(false);
      expect(facade.deviceOnline()).toBe(false);
    });
  });

  // -------------------------------------------------------------------------
  // GEST-FE-021: telemetry stub — emitTelemetry events at key transitions
  // -------------------------------------------------------------------------

  describe('[GEST-FE-021] telemetry — emitTelemetry stubs', () => {
    let consoleSpy: jest.SpyInstance;

    beforeEach(() => {
      consoleSpy = jest.spyOn(console, 'info').mockImplementation(() => undefined);
    });

    afterEach(() => {
      consoleSpy.mockRestore();
    });

    it('startTest emits [GEST-TELEMETRY] start-test event with comandoDraftRef', () => {
      facade.startTest(simpleDraft);

      expect(consoleSpy).toHaveBeenCalledWith(
        '[GEST-TELEMETRY]',
        expect.objectContaining({
          type: 'start-test',
          comandoDraftRef: simpleDraft.commandDraftRef,
        }),
      );
    });

    it('startTest does NOT emit stop-test when transitioning from idle (clean start)', () => {
      // startTest calls stopTest() internally, but since state is idle no stop-test is emitted
      facade.startTest(simpleDraft);

      expect(consoleSpy).not.toHaveBeenCalledWith(
        '[GEST-TELEMETRY]',
        expect.objectContaining({ type: 'stop-test' }),
      );
    });

    it('stopTest emits [GEST-TELEMETRY] stop-test event with numeric duration_ms when test is running', () => {
      facade.startTest(simpleDraft);
      consoleSpy.mockClear(); // discard the start-test call

      facade.stopTest();

      expect(consoleSpy).toHaveBeenCalledWith(
        '[GEST-TELEMETRY]',
        expect.objectContaining({ type: 'stop-test', duration_ms: expect.any(Number) }),
      );
    });

    it('stopTest does NOT emit stop-test when not in testing state (idle no-op)', () => {
      // Never called startTest — state is idle
      facade.stopTest();

      expect(consoleSpy).not.toHaveBeenCalledWith(
        '[GEST-TELEMETRY]',
        expect.objectContaining({ type: 'stop-test' }),
      );
    });

    it('finalize emits [GEST-TELEMETRY] verdict event after recognized signal', async () => {
      facade.startTest(simpleDraft);
      await flushCameraChain();
      consoleSpy.mockClear();

      signalSource.next(makeSignal({ signalKind: 'recognized', degraded: false }));

      expect(consoleSpy).toHaveBeenCalledWith(
        '[GEST-TELEMETRY]',
        expect.objectContaining({ type: 'verdict' }),
      );
    });

    it('degraded signal emits [GEST-TELEMETRY] degraded-activated event', async () => {
      facade.startTest(simpleDraft);
      await flushCameraChain();
      consoleSpy.mockClear();

      signalSource.next(makeSignal({ signalKind: 'degraded', degraded: true }));

      expect(consoleSpy).toHaveBeenCalledWith(
        '[GEST-TELEMETRY]',
        expect.objectContaining({ type: 'degraded-activated' }),
      );
    });
  });

  // -------------------------------------------------------------------------
  // ADR-006: interim detector banner — detectorType signal sync
  // Verifies that the facade reflects the port's detectorType signal in the
  // GestureTestVM, enabling the component to render the honest EAR/BRI banner.
  // These tests distinguish EAR/BRI (interim) from ONNX (production) and from
  // null (pre-handshake / mock path).
  // -------------------------------------------------------------------------

  describe('[ADR-006] detectorType — interim detector banner signal', () => {
    it('detectorType is null initially (pre-handshake)', () => {
      expect(facade.state().detectorType).toBeNull();
      expect(facade.detectorType()).toBeNull();
    });

    it('detectorType reflects "ear_bri" from port signal after effect flush — banner condition met', () => {
      detectorTypeSignal.set('ear_bri');
      TestBed.flushEffects();

      expect(facade.state().detectorType).toBe('ear_bri');
      expect(facade.detectorType()).toBe('ear_bri');
    });

    it('detectorType reflects "onnx" from port signal — banner condition NOT met', () => {
      detectorTypeSignal.set('onnx');
      TestBed.flushEffects();

      expect(facade.state().detectorType).toBe('onnx');
      // Verify the banner condition (==='ear_bri') is false for ONNX
      expect(facade.detectorType() === 'ear_bri').toBe(false);
    });

    it('detectorType resets to null when startTest is called — fresh per-test-run baseline', () => {
      // Simulate a previous test session with EAR/BRI
      detectorTypeSignal.set('ear_bri');
      TestBed.flushEffects();
      expect(facade.state().detectorType).toBe('ear_bri');

      // Reset the port signal (as adapter.close() would do)
      detectorTypeSignal.set(null);

      // Starting a new test resets detectorType immediately in _state
      facade.startTest(simpleDraft);

      expect(facade.state().detectorType).toBeNull();
    });

    it('detectorType resets to null when stopTest is called during testing', () => {
      // Arrange: start test, get the EAR/BRI handshake
      facade.startTest(simpleDraft);
      detectorTypeSignal.set('ear_bri');
      TestBed.flushEffects();
      expect(facade.state().detectorType).toBe('ear_bri');

      // Act: user clicks "Parar"
      facade.stopTest();

      // Assert: state transitions to idle and detectorType is cleared
      expect(facade.state().state).toBe('idle');
      expect(facade.state().detectorType).toBeNull();
    });

    it('mock path: detectorType stays null when port signal never changes — no banner on mock stream', () => {
      // detectorTypeSignal starts at null (mock adapter pattern)
      // Even after starting the test, detectorType should stay null
      facade.startTest(simpleDraft);
      TestBed.flushEffects();

      expect(facade.state().detectorType).toBeNull();
      expect(facade.detectorType()).toBeNull();
    });
  });

  // ---------------------------------------------------------------------------
  // Watchdog — ADR-007 follow-up 3
  // Sem supervisor de processo, detecção parada em silêncio é o pior modo de
  // falha do produto: a cuidadora acha que o canal está de pé e não está.
  //
  // Todos usam um draft de janela longa (60 s) de propósito: com o simpleDraft
  // (tempoMaximoGestoMs 5 s) o timeout da própria sessão dispara junto e zera as
  // flags, e o teste passaria sem provar nada sobre o watchdog.
  // ---------------------------------------------------------------------------
  describe('watchdog de detecção', () => {
    const watchdogDraft: ComandoDraft = { ...simpleDraft, tempoMaximoGestoMs: 60_000 };

    it('começa morto e vive ao primeiro sinal do motor', async () => {
      facade.startTest(watchdogDraft);
      await flushCameraChain();
      expect(facade.detectionAlive()).toBe(false);

      signalSource.next(ackSignal(watchdogDraft.commandDraftRef));

      expect(facade.detectionAlive()).toBe(true);
    });

    it('declara parado após 4 s sem batimento', async () => {
      facade.startTest(watchdogDraft);
      await flushCameraChain();
      signalSource.next(ackSignal(watchdogDraft.commandDraftRef));
      expect(facade.detectionAlive()).toBe(true);

      jest.advanceTimersByTime(6000);

      expect(facade.detectionAlive()).toBe(false);
    });

    it('segue vivo enquanto os batimentos chegam', async () => {
      facade.startTest(watchdogDraft);
      await flushCameraChain();

      for (let i = 0; i < 8; i++) {
        signalSource.next(ackSignal(watchdogDraft.commandDraftRef));
        jest.advanceTimersByTime(2000);
      }

      expect(facade.detectionAlive()).toBe(true);
    });

    // Instanciar o WASM do MediaPipe leva segundos: sem esta distinção a UI
    // acusaria "parada" durante a inicialização normal.
    it('não marca como iniciada antes do primeiro sinal, mesmo com o tempo passando', async () => {
      facade.startTest(watchdogDraft);
      await flushCameraChain();

      jest.advanceTimersByTime(10_000);

      expect(facade.detectionStarted()).toBe(false);
      expect(facade.detectionAlive()).toBe(false);
    });

    it('permanece iniciada quando os batimentos param — a sessão continua', async () => {
      facade.startTest(watchdogDraft);
      await flushCameraChain();
      signalSource.next(ackSignal(watchdogDraft.commandDraftRef));

      jest.advanceTimersByTime(6000);

      expect(facade.detectionStarted()).toBe(true);
      expect(facade.detectionAlive()).toBe(false);
    });

    it('para junto com o teste', async () => {
      facade.startTest(watchdogDraft);
      await flushCameraChain();
      signalSource.next(ackSignal(watchdogDraft.commandDraftRef));

      facade.stopTest();

      expect(facade.detectionAlive()).toBe(false);
      expect(facade.detectionStarted()).toBe(false);
    });
  });
});
