import type { FaceLandmarker as FaceLandmarkerType } from '@mediapipe/tasks-vision';
import { ENGINE_FEATURE, type EngineFeatureRef } from '@neuroassist/shared/contracts';

// =============================================================================
// GestureDetector — motor de detecção do produto (ADR-007)
//
// Portado de design-reference/angular-prototype/.../core/poc-gesture-detector.ts,
// validado em hardware real. Com a ADR-007 este deixou de ser um detector de
// protótipo: é o ÚNICO motor do produto, na calibração e no fluxo always-on.
// O agente Python (edge-agent-ws.py) saiu do caminho.
//
// Zero Angular aqui de propósito — classe pura, testável sem TestBed. Quem faz a
// ponte com o mundo Angular é EdgeDetectionBrowserAdapter.
//
// WASM e modelo vêm de assets locais (nunca CDN): a CSP de produção bloquearia o
// fetch e o always-on não pode depender de rede externa para começar a detectar.
// =============================================================================

/** Caminhos servidos pela app (ver `assets` em apps/neuroassist-ai-web/project.json). */
export interface DetectorAssetPaths {
  /** Diretório do runtime WASM do MediaPipe. */
  readonly wasmPath: string;
  /** Arquivo .task do FaceLandmarker. */
  readonly modelPath: string;
}

export const DEFAULT_ASSET_PATHS: DetectorAssetPaths = {
  wasmPath: '/assets/mediapipe-wasm',
  modelPath: '/assets/mediapipe-models/face_landmarker.task',
};

// ── Thresholds ──────────────────────────────────────────────────────────────
// Valores herdados do protótipo ("proven accurate on real hardware"). Divergem
// dos do edge-agent-ws.py (EAR_CLOSE 0.22, BRI em vez de blendshapes) — com o
// agente fora do caminho isso deixou de ser drift entre motores e passou a ser
// simplesmente a configuração do motor. Ver ADR-007, follow-up 6.
const EAR_CLOSE = 0.2;
const EAR_OPEN = 0.27;
const BLINK_MIN_FRAMES = 3;
const OPEN_MIN_FRAMES = 3;
const DOUBLE_BLINK_WINDOW_MS = 1800;

// eyeBlink{Left,Right}: 0 = aberto, 1 = fechado
const BLINK_BS_ON = 0.6;
const EYE_OTHER_MAX = 0.3; // o outro olho precisa ficar abaixo disso p/ contar como piscada única
const SINGLE_BLINK_FRAMES = 3;

// brow{InnerUp, OuterUpLeft, OuterUpRight}: 0 = neutro, 1 = totalmente levantada
const BROW_ON = 0.4;
const BROW_OFF = 0.18;
const BROW_MIN_FRAMES = 5; // ~167 ms a 30 fps

/** Cooldown compartilhado — impede que um mesmo gesto dispare em sequência. */
const COOLDOWN_MS = 4000;

const LEFT_EYE_IDX = [33, 160, 158, 133, 153, 144];
const RIGHT_EYE_IDX = [362, 385, 387, 263, 373, 380];

/**
 * O que este motor emite — em `engine_feature_ref`, o vocabulário canônico.
 *
 * <p>Era uma lista própria (`tg-piscar-duplo-001`, `tg-sobrancelha-dupla`, …)
 * que dizia "devem casar com o catálogo do PAC" e não casava com nada: o
 * catálogo oferecia `tg-piscar-duplo` e `tg-sobrancelha`. O comentário afirmava
 * a invariante; nenhum código a verificava.
 *
 * <p>Agora não há lista local a divergir — os nomes vêm de
 * `@neuroassist/shared/contracts`, os mesmos que o banco grava em
 * `tipo_gesto.engine_feature_ref`.
 */
export { ENGINE_FEATURE };

type BlinkState =
  | 'WAITING_BLINK1'
  | 'IN_BLINK1'
  | 'WAITING_OPEN1'
  | 'WAITING_BLINK2'
  | 'IN_BLINK2'
  | 'DONE';

/** Telemetria por frame — alimenta o indicador de "detecção ativa" (watchdog da ADR-007). */
export interface GestureFrame {
  readonly faceDetected: boolean;
  readonly earAvg: number;
  readonly earLeft: number;
  readonly earRight: number;
  readonly blinkCount: number;
}

interface Landmark {
  readonly x: number;
  readonly y: number;
}

/** "Ativo por N frames seguidos, então dispara uma vez" — com rearme na soltura. */
class OnsetDetector {
  private frameCount = 0;
  private alreadyFired = false;

  process(active: boolean, minFrames: number): boolean {
    if (active) {
      this.frameCount++;
      if (!this.alreadyFired && this.frameCount >= minFrames) {
        this.alreadyFired = true;
        return true;
      }
    } else {
      this.frameCount = 0;
      this.alreadyFired = false;
    }
    return false;
  }

  reset(): void {
    this.frameCount = 0;
    this.alreadyFired = false;
  }
}

export interface GestureDetectorCallbacks {
  /** Chamado a cada frame processado (com ou sem rosto). */
  readonly onFrame: (frame: GestureFrame) => void;
  /** Chamado quando um gesto completa. Emite o nome CANÔNICO do motor. */
  readonly onGesture: (engineFeatureRef: EngineFeatureRef, confidence: number) => void;
}

/** Conexão entre dois landmarks, no formato que o MediaPipe publica. */
interface Conexao {
  readonly start: number;
  readonly end: number;
}

/** As listas de conexões são estáticas na classe FaceLandmarker, e só existem
 * depois do import dinâmico — por isso são capturadas no `init()`. */
interface ConexoesDaMalha {
  readonly tesselacao: readonly Conexao[];
  readonly contorno: readonly Conexao[];
  readonly destaques: readonly Conexao[];
}

export class GestureDetector {
  private landmarker: FaceLandmarkerType | null = null;
  private rafId: number | null = null;
  private active = false;
  private video: HTMLVideoElement | null = null;

  /** Canvas onde a malha é desenhada. `null` = não desenhar (o motor roda igual). */
  private overlay: HTMLCanvasElement | null = null;
  private conexoes: ConexoesDaMalha | null = null;

  private blinkState: BlinkState = 'WAITING_BLINK1';
  private blinkFrameCount = 0;
  private openFrameCount = 0;
  private firstBlinkCompletedMs: number | null = null;
  private blinkCount = 0;
  private cooldownUntil = 0;

  private readonly leftBlink = new OnsetDetector();
  private readonly rightBlink = new OnsetDetector();
  private readonly browDual = new OnsetDetector();
  private readonly browLeft = new OnsetDetector();
  private readonly browRight = new OnsetDetector();

  constructor(
    private readonly callbacks: GestureDetectorCallbacks,
    private readonly assets: DetectorAssetPaths = DEFAULT_ASSET_PATHS,
  ) {}

  /** Carrega WASM + modelo. Import dinâmico para o bundle do MediaPipe não entrar
   * no caminho inicial da aplicação (ADR-007: lazy-load obrigatório). */
  async init(): Promise<void> {
    const { FaceLandmarker, FilesetResolver } = await import('@mediapipe/tasks-vision');
    const fileset = await FilesetResolver.forVisionTasks(this.assets.wasmPath);

    this.landmarker = await FaceLandmarker.createFromOptions(fileset, {
      baseOptions: { modelAssetPath: this.assets.modelPath, delegate: 'CPU' },
      runningMode: 'VIDEO',
      numFaces: 1,
      outputFaceBlendshapes: true,
    });

    this.conexoes = {
      tesselacao: FaceLandmarker.FACE_LANDMARKS_TESSELATION,
      contorno: FaceLandmarker.FACE_LANDMARKS_FACE_OVAL,
      // Olhos e sobrancelhas juntos e em destaque: são exatamente as regiões de
      // onde saem os seis gestos. Ver a pessoa "acender" a região que ela está
      // movendo é o que transforma a malha em resposta, e não em enfeite.
      destaques: [
        ...FaceLandmarker.FACE_LANDMARKS_LEFT_EYE,
        ...FaceLandmarker.FACE_LANDMARKS_RIGHT_EYE,
        ...FaceLandmarker.FACE_LANDMARKS_LEFT_EYEBROW,
        ...FaceLandmarker.FACE_LANDMARKS_RIGHT_EYEBROW,
      ],
    };
  }

  /**
   * Liga (ou desliga, com `null`) o desenho da malha facial.
   *
   * <p>Pode ser chamado antes ou depois do `start()` — o desenho é lido a cada
   * frame, não capturado no início.
   *
   * <p>É opcional de propósito: a malha é **retorno visual**, não detecção. Sem
   * canvas o motor roda idêntico, que é o caso do fluxo always-on, onde não há
   * ninguém olhando para a tela. O custo de desenhar ~2.600 segmentos por frame
   * não deve ser pago por quem não vai vê-los.
   */
  attachOverlay(canvas: HTMLCanvasElement | null): void {
    this.overlay = canvas;
    if (!canvas) return;
    const ctx = canvas.getContext('2d');
    ctx?.clearRect(0, 0, canvas.width, canvas.height);
  }

  /** Começa a processar frames do elemento de vídeo. `init()` precisa ter resolvido. */
  start(video: HTMLVideoElement): void {
    if (!this.landmarker) {
      throw new Error('GestureDetector.start() antes de init() — modelo não carregado.');
    }
    this.video = video;
    this.active = true;
    this.resetAll();
    this.tick();
  }

  stop(): void {
    this.active = false;
    if (this.rafId !== null) {
      cancelAnimationFrame(this.rafId);
      this.rafId = null;
    }
    // Limpa ANTES de soltar o vídeo: `desenharMalha` usa as dimensões dele para
    // dimensionar o canvas. Parar sem limpar deixaria a última malha desenhada
    // sobre um preview já encerrado.
    this.desenharMalha(undefined);
    this.video = null;
  }

  destroy(): void {
    this.stop();
    this.landmarker?.close();
    this.landmarker = null;
  }

  // ---------------------------------------------------------------------------

  private resetAll(): void {
    this.blinkState = 'WAITING_BLINK1';
    this.blinkFrameCount = 0;
    this.openFrameCount = 0;
    this.firstBlinkCompletedMs = null;
    this.blinkCount = 0;
    this.cooldownUntil = 0; // sempre limpo ao reiniciar — sem bloqueio residual entre testes
    this.leftBlink.reset();
    this.rightBlink.reset();
    this.browDual.reset();
    this.browLeft.reset();
    this.browRight.reset();
  }

  /**
   * Desenha a malha facial sobre o preview. `face` ausente = limpa e sai.
   *
   * <p>Portado do `drawGrid()` do protótipo. Três camadas, e a hierarquia é
   * deliberada: a tesselação inteira quase transparente diz "estou vendo seu
   * rosto"; o contorno mais firme mostra o enquadramento; olhos e sobrancelhas
   * em verde vivo são as regiões que produzem os seis gestos — é onde a pessoa
   * precisa olhar para entender o que o motor está lendo.
   *
   * <p>O canvas é redimensionado para o tamanho do vídeo, não do CSS: as
   * coordenadas do MediaPipe são normalizadas (0..1), então qualquer descompasso
   * entre o buffer e o vídeo desloca a malha em relação ao rosto — e uma malha
   * deslocada parece defeito de detecção quando é só escala.
   */
  private desenharMalha(face: Landmark[] | undefined): void {
    const canvas = this.overlay;
    if (!canvas) return;

    const ctx = canvas.getContext('2d');
    if (!ctx) return;

    const video = this.video;
    const w = video?.videoWidth ?? 0;
    const h = video?.videoHeight ?? 0;
    if (w > 0 && h > 0 && (canvas.width !== w || canvas.height !== h)) {
      canvas.width = w;
      canvas.height = h;
    }

    ctx.clearRect(0, 0, canvas.width, canvas.height);
    if (!face || !this.conexoes) return;

    const { width: W, height: H } = canvas;
    const traco = (conexoes: readonly Conexao[]): void => {
      ctx.beginPath();
      for (const c of conexoes) {
        const a = face[c.start];
        const b = face[c.end];
        if (!a || !b) continue;
        ctx.moveTo(a.x * W, a.y * H);
        ctx.lineTo(b.x * W, b.y * H);
      }
      // Um único path para as ~2.600 conexões da tesselação. O protótipo abria um
      // beginPath/stroke por segmento; a 30 fps isso é ~78.000 chamadas por
      // segundo, e o custo aparecia como queda de fps no próprio detector.
      ctx.stroke();
    };

    ctx.lineWidth = 0.5;
    ctx.strokeStyle = 'rgba(80, 200, 255, 0.13)';
    traco(this.conexoes.tesselacao);

    ctx.lineWidth = 1;
    ctx.strokeStyle = 'rgba(80, 200, 255, 0.45)';
    traco(this.conexoes.contorno);

    ctx.lineWidth = 2;
    ctx.strokeStyle = 'rgba(0, 255, 180, 0.92)';
    traco(this.conexoes.destaques);

    // Os pontos que ALIMENTAM o cálculo do EAR — os mesmos índices usados em
    // calcEar(). Quem calibra consegue ver o que o número está medindo.
    ctx.fillStyle = 'rgba(0, 255, 180, 0.9)';
    for (const idx of [...LEFT_EYE_IDX, ...RIGHT_EYE_IDX]) {
      const p = face[idx];
      if (!p) continue;
      ctx.beginPath();
      ctx.arc(p.x * W, p.y * H, 2.5, 0, Math.PI * 2);
      ctx.fill();
    }
  }

  private fire(engineFeatureRef: EngineFeatureRef, now: number, confidence: number): void {
    // Ordem importa: resetAll() zera o cooldown, então ele é armado DEPOIS.
    // No protótipo a ordem era inversa e o cooldown nunca valia nada — um gesto
    // sustentado podia disparar repetidamente. Corrigido aqui de propósito.
    this.resetAll();
    this.cooldownUntil = now + COOLDOWN_MS;
    this.callbacks.onGesture(engineFeatureRef, confidence);
  }

  private tick(): void {
    if (!this.active || !this.landmarker || !this.video) return;

    const video = this.video;
    const now = performance.now();

    if (video.readyState >= 2 && !video.paused) {
      let result;
      try {
        result = this.landmarker.detectForVideo(video, now);
      } catch {
        // Frame ruim não derruba a sessão — mas o batimento PRECISA continuar,
        // senão o watchdog acusa parada e o erro fica invisível.
        this.desenharMalha(undefined);
        this.callbacks.onFrame(NO_FACE);
        this.rafId = requestAnimationFrame(() => this.tick());
        return;
      }

      const face = result.faceLandmarks?.[0] as Landmark[] | undefined;

      // A malha é limpa TODO frame, com rosto ou sem. Sem isto o último desenho
      // fica congelado na tela quando a pessoa sai de quadro — uma malha parada
      // sobre um rosto ausente afirma que o motor está vendo o que não vê.
      this.desenharMalha(face);

      if (face) {
        const earL = calcEar(face, LEFT_EYE_IDX);
        const earR = calcEar(face, RIGHT_EYE_IDX);
        const earAvg = (earL + earR) / 2;

        const categories = result.faceBlendshapes?.[0]?.categories;
        const score = (name: string): number =>
          categories?.find((c) => c.categoryName === name)?.score ?? 0;

        const eyeBlinkL = score('eyeBlinkLeft');
        const eyeBlinkR = score('eyeBlinkRight');
        const browInnerUp = score('browInnerUp');
        const browOuterUpL = score('browOuterUpLeft');
        const browOuterUpR = score('browOuterUpRight');

        if (now >= this.cooldownUntil) {
          this.processDoubleBlink(earAvg, now);

          if (this.leftBlink.process(eyeBlinkL > BLINK_BS_ON && eyeBlinkR < EYE_OTHER_MAX, SINGLE_BLINK_FRAMES)) {
            this.fire(ENGINE_FEATURE.blinkLeft, now, eyeBlinkL);
          }
          if (this.rightBlink.process(eyeBlinkR > BLINK_BS_ON && eyeBlinkL < EYE_OTHER_MAX, SINGLE_BLINK_FRAMES)) {
            this.fire(ENGINE_FEATURE.blinkRight, now, eyeBlinkR);
          }
          if (this.browDual.process(browInnerUp > BROW_ON, BROW_MIN_FRAMES)) {
            this.fire(ENGINE_FEATURE.browRaiseBoth, now, browInnerUp);
          }
          if (this.browLeft.process(browOuterUpL > BROW_ON && browOuterUpR < BROW_OFF, BROW_MIN_FRAMES)) {
            this.fire(ENGINE_FEATURE.browRaiseLeft, now, browOuterUpL);
          }
          if (this.browRight.process(browOuterUpR > BROW_ON && browOuterUpL < BROW_OFF, BROW_MIN_FRAMES)) {
            this.fire(ENGINE_FEATURE.browRaiseRight, now, browOuterUpR);
          }
        }

        this.callbacks.onFrame({
          faceDetected: true,
          earAvg,
          earLeft: earL,
          earRight: earR,
          blinkCount: this.blinkCount,
        });
      } else {
        this.callbacks.onFrame(NO_FACE);
      }
    } else {
      this.callbacks.onFrame(NO_FACE);
    }

    this.rafId = requestAnimationFrame(() => this.tick());
  }

  /** Máquina de estados da piscada dupla (baseada em EAR). */
  private processDoubleBlink(earAvg: number, now: number): void {
    const eyeClosed = earAvg < EAR_CLOSE;
    const eyeOpen = earAvg >= EAR_OPEN;

    // Janela da segunda piscada expirou — recomeça.
    if (
      this.blinkState === 'WAITING_BLINK2' &&
      this.firstBlinkCompletedMs !== null &&
      now - this.firstBlinkCompletedMs > DOUBLE_BLINK_WINDOW_MS
    ) {
      this.blinkState = 'WAITING_BLINK1';
      this.blinkFrameCount = 0;
      this.openFrameCount = 0;
      this.firstBlinkCompletedMs = null;
      this.blinkCount = 0;
    }

    switch (this.blinkState) {
      case 'WAITING_BLINK1':
        if (eyeClosed) {
          this.blinkState = 'IN_BLINK1';
          this.blinkFrameCount = 1;
        }
        break;

      case 'IN_BLINK1':
        if (eyeClosed) {
          this.blinkFrameCount++;
        } else if (eyeOpen) {
          if (this.blinkFrameCount >= BLINK_MIN_FRAMES) {
            this.blinkState = 'WAITING_OPEN1';
            this.openFrameCount = 1;
            this.firstBlinkCompletedMs = now;
            this.blinkCount = 1;
          } else {
            this.blinkState = 'WAITING_BLINK1';
            this.blinkFrameCount = 0;
          }
        }
        break;

      case 'WAITING_OPEN1':
        if (eyeOpen) {
          this.openFrameCount++;
          if (this.openFrameCount >= OPEN_MIN_FRAMES) {
            this.blinkState = 'WAITING_BLINK2';
          }
        } else if (eyeClosed) {
          this.blinkFrameCount++;
        }
        break;

      case 'WAITING_BLINK2':
        if (eyeClosed) {
          this.blinkState = 'IN_BLINK2';
          this.blinkFrameCount = 1;
        }
        break;

      case 'IN_BLINK2':
        if (eyeClosed) {
          this.blinkFrameCount++;
        } else if (eyeOpen) {
          const dentroDaJanela =
            this.firstBlinkCompletedMs !== null &&
            now - this.firstBlinkCompletedMs <= DOUBLE_BLINK_WINDOW_MS;
          if (this.blinkFrameCount >= BLINK_MIN_FRAMES && dentroDaJanela) {
            this.blinkState = 'DONE';
            this.blinkCount = 2;
            this.fire(ENGINE_FEATURE.blinkDouble, now, 1 - earAvg / EAR_CLOSE);
          } else {
            this.blinkState = 'WAITING_BLINK1';
            this.blinkFrameCount = 0;
          }
        }
        break;

      case 'DONE':
        break;
    }
  }
}

const NO_FACE: GestureFrame = {
  faceDetected: false,
  earAvg: 0,
  earLeft: 0,
  earRight: 0,
  blinkCount: 0,
};

/** Eye Aspect Ratio — razão entre as distâncias verticais e a horizontal do olho. */
function calcEar(landmarks: readonly Landmark[], idx: readonly number[]): number {
  const p = idx.map((i) => landmarks[i]);
  const d = (a: Landmark, b: Landmark): number => Math.hypot(a.x - b.x, a.y - b.y);
  const horizontal = d(p[0], p[3]);
  if (horizontal === 0) return 0;
  return (d(p[1], p[5]) + d(p[2], p[4])) / (2 * horizontal);
}
