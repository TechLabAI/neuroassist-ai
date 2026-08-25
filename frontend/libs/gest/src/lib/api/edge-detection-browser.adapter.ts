import { Injectable, signal } from '@angular/core';
import { Observable } from 'rxjs';
import { ComandoDraft, DetectorType, EdgeRecognitionSignal } from '@neuroassist/shared/contracts';
import { EdgeDetectionPort } from './edge-detection.port';
import { GestureDetector } from '../detection/gesture-detector';

const CONTRACT_VERSION = '1.0.0';

/** Intervalo mínimo entre batimentos de watchdog (preview_frame_ack). */
const ACK_INTERVAL_MS = 1000;

// =============================================================================
// EdgeDetectionBrowserAdapter — ADR-007
//
// O motor de detecção roda AQUI, no browser. Substitui a ponte WS com o agente
// Python (EdgeDetectionHttpAdapter), que a ADR-007 tirou do caminho do produto.
//
// Satisfaz o mesmo EdgeDetectionPort de propósito: GestureTestFacade e
// GestureLiveTestComponent não mudam. A única diferença visível para o facade é
// que openStream() recebe o MediaStream que ele já adquiriu — não faz sentido
// abrir a câmera duas vezes, e em alguns dispositivos o segundo getUserMedia no
// mesmo device falha.
//
// Sem stream não há detecção real: cai no degradado honesto, nunca num
// "recognized" falso (mesma regra da ADR-005 que continua valendo).
// =============================================================================

@Injectable()
export class EdgeDetectionBrowserAdapter implements EdgeDetectionPort {
  private detector: GestureDetector | null = null;
  private video: HTMLVideoElement | null = null;

  /** Guardado no adaptador, e não no detector, porque o canvas costuma chegar
   * ANTES da sessão existir: a view do componente inicializa primeiro. Sem isto
   * o overlay só valeria a partir da segunda execução do teste. */
  private overlay: HTMLCanvasElement | null = null;

  private readonly _detectorType = signal<DetectorType | null>(null);
  /** ADR-007: 'browser_mediapipe' assim que o modelo carrega; null antes disso e após close(). */
  readonly detectorType = this._detectorType.asReadonly();

  openStream(draft: ComandoDraft, source?: MediaStream): Observable<EdgeRecognitionSignal> {
    if (!source) {
      // O facade só chega aqui depois de adquirir a câmera; sem stream é bug ou
      // permissão negada. Degradar é honesto — inventar "recognized" não seria.
      return this.mockStream(draft);
    }

    if (draft.engineFeatureRefPrincipal === null) {
      // O comando aponta para um tipo de gesto que o motor não detecta. Sem esta
      // guarda o teste ficava girando para sempre: nenhuma emissão do detector
      // casaria, e a tela mostraria "testando…" indefinidamente — a espera
      // eterna é pior que o veredito ruim, porque não informa nada.
      return this.mockStream(draft);
    }

    return new Observable<EdgeRecognitionSignal>((subscriber) => {
      const startedAt = performance.now();
      let cancelled = false;

      // <video> não anexado ao DOM: serve só de fonte de frames para o
      // detectForVideo(). O preview visível é do componente, com o mesmo stream.
      const video = document.createElement('video');
      video.srcObject = source;
      video.muted = true;
      video.playsInline = true;
      this.video = video;

      // O facade escreve estado a cada preview_frame_ack; a ~30 fps isso seriam
      // 30 ciclos de detecção de mudanças por segundo, sem nada novo para mostrar.
      // O ack é batimento de watchdog, não dado de tela: 1/s basta — mais uma
      // emissão imediata sempre que o rosto entra ou sai de quadro, que é a única
      // transição que a cuidadora precisa perceber na hora.
      let ultimoAckMs = 0;
      let rostoAnterior: boolean | null = null;

      const detector = new GestureDetector({
        onFrame: (frame) => {
          const agora = performance.now();
          const mudouEnquadramento = frame.faceDetected !== rostoAnterior;
          if (mudouEnquadramento || agora - ultimoAckMs >= ACK_INTERVAL_MS) {
            rostoAnterior = frame.faceDetected;
            ultimoAckMs = agora;
            subscriber.next(buildSignal(draft, 'preview_frame_ack', startedAt, []));
          }
        },
        onGesture: (engineFeatureRef, confidence) => {
          // Compara REFERÊNCIA DE MOTOR com referência de motor.
          //
          // Antes era `tipoGestoId === draft.primitivoPrincipal`: o id que o
          // motor emite contra o id do CATÁLOGO. Vocabulários diferentes — a
          // igualdade era falsa por construção, e o teste ao vivo jamais
          // reconheceria um gesto em produção. Passava despercebido porque os
          // testes do adaptador montavam o draft com os ids do próprio motor.
          const esperado =
            engineFeatureRef === draft.engineFeatureRefPrincipal ||
            engineFeatureRef === draft.engineFeatureRefSecundario;
          subscriber.next(
            buildSignal(
              draft,
              esperado ? 'recognized' : 'not_recognized',
              startedAt,
              [{ engineFeatureRef, confidence }],
            ),
          );
        },
      });
      this.detector = detector;
      detector.attachOverlay(this.overlay);

      void (async () => {
        try {
          await detector.init();
          await video.play();
          if (cancelled) return;
          this._detectorType.set('browser_mediapipe');
          detector.start(video);
        } catch {
          // Falha ao carregar WASM/modelo ou a dar play: degradado explícito.
          if (cancelled) return;
          this._detectorType.set(null);
          subscriber.next(buildSignal(draft, 'degraded', startedAt, [], true));
        }
      })();

      return () => {
        cancelled = true;
        // Derruba ESTA sessão, não "a sessão atual": em um retry o teardown da
        // anterior chegava depois do openStream da nova e destruía o detector
        // recém-criado. O sintoma era cruel — a primeira execução funcionava, a
        // segunda emitia um frame e morria calada.
        this.encerrarSessao(detector, video);
      };
    });
  }

  /** Stream degradado explícito — nunca emite um "recognized" real. */
  mockStream(draft: ComandoDraft): Observable<EdgeRecognitionSignal> {
    return new Observable<EdgeRecognitionSignal>((subscriber) => {
      subscriber.next(buildSignal(draft, 'degraded', performance.now(), [], true));
    });
  }

  close(): void {
    this.encerrarSessao(this.detector, this.video);
    this._detectorType.set(null);
  }

  attachOverlay(canvas: HTMLCanvasElement | null): void {
    this.overlay = canvas;
    // Repassa também à sessão em curso: se a view inicializar depois do start
    // (retry, troca de câmera), sem isto a malha só apareceria na próxima sessão.
    this.detector?.attachOverlay(canvas);
  }

  /**
   * Encerra a sessão dona de `detector`/`video`.
   *
   * Os campos de instância só são limpos se ainda apontarem para esta sessão —
   * é o que impede o teardown atrasado de uma sessão antiga de matar a nova.
   *
   * As tracks do MediaStream não são paradas aqui: pertencem a quem as adquiriu
   * (o facade), e pará-las mataria o preview do componente.
   */
  private encerrarSessao(
    detector: GestureDetector | null,
    video: HTMLVideoElement | null,
  ): void {
    detector?.destroy();
    if (video) {
      video.srcObject = null;
    }
    if (this.detector === detector) {
      this.detector = null;
    }
    if (this.video === video) {
      this.video = null;
    }
  }
}

function buildSignal(
  draft: ComandoDraft,
  signalKind: EdgeRecognitionSignal['signalKind'],
  startedAt: number,
  primitives: EdgeRecognitionSignal['primitives'],
  degraded = false,
): EdgeRecognitionSignal {
  const endMs = performance.now();
  return {
    contractVersion: CONTRACT_VERSION,
    signalKind,
    commandDraftRef: draft.commandDraftRef,
    primitives,
    timingWindow: {
      startMs: Math.round(startedAt),
      endMs: Math.round(endMs),
      durationMs: Math.round(endMs - startedAt),
    },
    degraded,
    emittedAt: new Date().toISOString(),
  };
}
