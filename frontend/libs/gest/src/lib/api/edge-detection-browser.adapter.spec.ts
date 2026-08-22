import { GestureDetector } from '../detection/gesture-detector';
import { EdgeDetectionBrowserAdapter } from './edge-detection-browser.adapter';
import { ComandoDraft, EdgeRecognitionSignal } from '@neuroassist/shared/contracts';

jest.mock('../detection/gesture-detector');

// ---------------------------------------------------------------------------
// EdgeDetectionBrowserAdapter — ADR-007
//
// GestureDetector é mockado: o real instancia ~11 MB de WASM e precisa de
// webcam, nada disso roda em jsdom. O que interessa aqui é o ciclo de vida das
// sessões, que foi onde o bug morava.
// ---------------------------------------------------------------------------

const DetectorMock = GestureDetector as jest.MockedClass<typeof GestureDetector>;

const draft: ComandoDraft = {
  commandDraftRef: 'draft-001',
  rotulo: 'Água',
  primitivoPrincipal: 'tg-piscar-duplo-001',
  primitivoSecundario: null,
  sensibilidade: 'media',
  urgencia: 'rotina',
  tempoMinimoGestoMs: 500,
  tempoMaximoGestoMs: 5000,
  composite: false,
};

/** Instâncias criadas, na ordem — uma por sessão. */
function instancias(): Array<{ init: jest.Mock; start: jest.Mock; destroy: jest.Mock }> {
  return DetectorMock.mock.instances as never;
}

/** Deixa o encadeamento init() → play() → start() completar. */
async function flush(): Promise<void> {
  await Promise.resolve();
  await Promise.resolve();
  await Promise.resolve();
}

describe('EdgeDetectionBrowserAdapter', () => {
  let adapter: EdgeDetectionBrowserAdapter;
  let stream: MediaStream;

  beforeEach(() => {
    DetectorMock.mockClear();
    DetectorMock.prototype.init = jest.fn().mockResolvedValue(undefined);
    DetectorMock.prototype.start = jest.fn();
    DetectorMock.prototype.destroy = jest.fn();

    // jsdom não implementa play() nem srcObject de verdade.
    HTMLMediaElement.prototype.play = jest.fn().mockResolvedValue(undefined);

    stream = { getVideoTracks: () => [], getTracks: () => [] } as unknown as MediaStream;
    adapter = new EdgeDetectionBrowserAdapter();
  });

  it('sem MediaStream cai no degradado — nunca num "recognized" falso', (done) => {
    adapter.openStream(draft).subscribe((sig: EdgeRecognitionSignal) => {
      expect(sig.signalKind).toBe('degraded');
      expect(sig.degraded).toBe(true);
      done();
    });
  });

  it('informa browser_mediapipe como motor depois de carregar', async () => {
    expect(adapter.detectorType()).toBeNull();

    const sub = adapter.openStream(draft, stream).subscribe();
    await flush();

    expect(adapter.detectorType()).toBe('browser_mediapipe');
    sub.unsubscribe();
  });

  // Regressão: o teardown usava os campos de instância, então ao encerrar a
  // sessão anterior ele destruía o detector da NOVA. A primeira execução
  // funcionava e a segunda emitia um frame e morria calada.
  it('teardown de uma sessão antiga não derruba a sessão nova', async () => {
    const sessao1 = adapter.openStream(draft, stream).subscribe();
    await flush();
    const sessao2 = adapter.openStream(draft, stream).subscribe();
    await flush();

    expect(instancias()).toHaveLength(2);
    const [detector1, detector2] = instancias();

    sessao1.unsubscribe(); // teardown atrasado da primeira

    expect(detector1.destroy).toHaveBeenCalled();
    expect(detector2.destroy).not.toHaveBeenCalled();

    sessao2.unsubscribe();
    expect(detector2.destroy).toHaveBeenCalled();
  });

  it('close() derruba a sessão corrente', async () => {
    const sub = adapter.openStream(draft, stream).subscribe();
    await flush();

    adapter.close();

    expect(instancias()[0].destroy).toHaveBeenCalled();
    expect(adapter.detectorType()).toBeNull();
    sub.unsubscribe();
  });

  it('falha ao carregar o modelo vira degradado explícito, não silêncio', async () => {
    DetectorMock.prototype.init = jest.fn().mockRejectedValue(new Error('wasm indisponível'));
    const recebidos: EdgeRecognitionSignal[] = [];

    const sub = adapter.openStream(draft, stream).subscribe((s) => recebidos.push(s));
    await flush();

    expect(recebidos.map((s) => s.signalKind)).toEqual(['degraded']);
    expect(adapter.detectorType()).toBeNull();
    sub.unsubscribe();
  });
});
