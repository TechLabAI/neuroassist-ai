import { GestureDetector } from '../detection/gesture-detector';
import { EdgeDetectionBrowserAdapter } from './edge-detection-browser.adapter';
import { ComandoDraft, EdgeRecognitionSignal, ENGINE_FEATURE } from '@neuroassist/shared/contracts';

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
  primitivoPrincipal: 'catalogo-uuid-qualquer',
  primitivoSecundario: null,
  // A referência de MOTOR é o que o adaptador compara — deliberadamente
  // diferente do id de catálogo acima, porque foi exatamente essa confusão que
  // produziu o defeito: comparar id de catálogo com emissão de motor.
  engineFeatureRefPrincipal: ENGINE_FEATURE.blinkDouble,
  engineFeatureRefSecundario: null,
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

  // ---------------------------------------------------------------------------
  // O casamento gesto ↔ comando.
  //
  // Aqui morava o defeito que nenhum gate pegava: o adaptador comparava o id que
  // o MOTOR emite com `draft.primitivoPrincipal`, que é id de CATÁLOGO. A
  // igualdade era falsa por construção — o teste ao vivo jamais reconheceria um
  // gesto em produção.
  //
  // E o teste antigo passava justamente por causa do defeito: o draft de teste
  // era montado com os ids do próprio motor, de modo que os dois vocabulários
  // coincidiam SÓ dentro do teste. É o defeito nº 1 catalogado deste repositório
  // — o consumidor escrito a partir da própria suposição, verificado contra ela.
  //
  // Por isso o fixture acima usa um id de catálogo deliberadamente diferente da
  // referência de motor: se alguém reintroduzir a comparação errada, estes dois
  // cenários trocam de resultado.
  // ---------------------------------------------------------------------------

  /** Dispara o callback de gesto da sessão ativa, como o detector real faria. */
  function emitirGesto(ref: string, confianca = 0.9): void {
    const opcoes = DetectorMock.mock.calls[0][0] as {
      onGesture: (r: string, c: number) => void;
    };
    opcoes.onGesture(ref, confianca);
  }

  it('gesto ESPERADO reconhece — compara referência de motor, não id de catálogo', async () => {
    const recebidos: EdgeRecognitionSignal[] = [];
    const sub = adapter.openStream(draft, stream).subscribe((s) => recebidos.push(s));
    await flush();

    emitirGesto(ENGINE_FEATURE.blinkDouble);

    const veredito = recebidos.find((s) => s.signalKind !== 'preview_frame_ack');
    expect(veredito?.signalKind).toBe('recognized');
    expect(veredito?.primitives[0].engineFeatureRef).toBe(ENGINE_FEATURE.blinkDouble);
    sub.unsubscribe();
  });

  it('gesto DIFERENTE do configurado não reconhece', async () => {
    const recebidos: EdgeRecognitionSignal[] = [];
    const sub = adapter.openStream(draft, stream).subscribe((s) => recebidos.push(s));
    await flush();

    emitirGesto(ENGINE_FEATURE.browRaiseBoth);

    const veredito = recebidos.find((s) => s.signalKind !== 'preview_frame_ack');
    expect(veredito?.signalKind).toBe('not_recognized');
    sub.unsubscribe();
  });

  it('comando sem motor degrada em vez de esperar para sempre', async () => {
    // O comando aponta para um tipo de gesto que o detector não emite. Sem a
    // guarda o teste ficava em "testando…" indefinidamente — nenhuma emissão
    // casaria nunca, e a espera eterna não informa nada a quem está calibrando.
    const semMotor: ComandoDraft = {
      ...draft,
      engineFeatureRefPrincipal: null,
      engineFeatureRefSecundario: null,
    };
    const recebidos: EdgeRecognitionSignal[] = [];

    const sub = adapter.openStream(semMotor, stream).subscribe((s) => recebidos.push(s));
    await flush();

    expect(recebidos.map((s) => s.signalKind)).toEqual(['degraded']);
    // E não chegou a abrir sessão de detecção — nada a destruir depois.
    expect(DetectorMock.mock.instances).toHaveLength(0);
    sub.unsubscribe();
  });

  it('overlay chega ao detector da sessão, e é solto no fim', async () => {
    DetectorMock.prototype.attachOverlay = jest.fn();
    const canvas = document.createElement('canvas');
    adapter.attachOverlay(canvas);

    const sub = adapter.openStream(draft, stream).subscribe();
    await flush();

    expect(instancias()[0].attachOverlay as unknown as jest.Mock).toHaveBeenCalledWith(canvas);

    // A view some (estado deixa de ser testing) → o canvas precisa ser soltado,
    // senão o adaptador desenha em memória, num elemento fora do DOM.
    adapter.attachOverlay(null);
    expect(instancias()[0].attachOverlay as unknown as jest.Mock).toHaveBeenCalledWith(null);
    sub.unsubscribe();
  });
});
