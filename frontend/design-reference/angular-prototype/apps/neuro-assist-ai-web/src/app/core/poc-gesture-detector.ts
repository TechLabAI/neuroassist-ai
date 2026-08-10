import { FaceLandmarker, FilesetResolver } from '@mediapipe/tasks-vision';

// WASM served from node_modules via Angular assets config (no CDN dependency)
const WASM_PATH = '/assets/mediapipe-wasm';

// ── EAR thresholds (double-blink detection — proven accurate on real hardware) ──
const EAR_CLOSE          = 0.20;
const EAR_OPEN           = 0.27;
const BLINK_MIN_FRAMES   = 3;
const OPEN_MIN_FRAMES    = 3;
const DOUBLE_BLINK_WINDOW_MS = 1800;

// ── Blendshape thresholds (single-eye blink & eyebrow raise) ──
// eyeBlink{Left,Right}: 0 = open, 1 = closed
const BLINK_BS_ON   = 0.60;   // score to count as "closed"
const BLINK_BS_OFF  = 0.25;   // score to count as "open" again (hysteresis)
const EYE_OTHER_MAX = 0.30;   // other eye must stay below this for a single-eye blink
const SINGLE_BLINK_FRAMES = 3;

// brow{InnerUp, OuterUpLeft, OuterUpRight}: 0 = neutral, 1 = fully raised
const BROW_ON          = 0.40;  // score to count as "raised"
const BROW_OFF         = 0.18;  // score to count as "relaxed"
const BROW_MIN_FRAMES  = 5;     // sustained raise (~167ms at 30fps)

// Shared cooldown across all gestures — prevents duplicate firing
const COOLDOWN_MS = 4000;

const LEFT_EYE_IDX  = [33, 160, 158, 133, 153, 144];
const RIGHT_EYE_IDX = [362, 385, 387, 263, 373, 380];

// ── Double-blink state machine states ──
type BlinkState =
  | 'WAITING_BLINK1' | 'IN_BLINK1' | 'WAITING_OPEN1'
  | 'WAITING_BLINK2' | 'IN_BLINK2' | 'DONE';

export interface GestureFrame {
  faceDetected: boolean;
  earAvg:       number;
  earLeft:      number;
  earRight:     number;
  blinkCount:   number;
}

// Simple "hold active for N frames then fire once" helper
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
      this.frameCount   = 0;
      this.alreadyFired = false;
    }
    return false;
  }

  reset(): void { this.frameCount = 0; this.alreadyFired = false; }
}

export class PocGestureDetector {
  private landmarker: FaceLandmarker | null = null;
  private rafId: number | null = null;
  private active = false;

  // ── Double-blink state machine ──
  private blinkState: BlinkState     = 'WAITING_BLINK1';
  private blinkFrameCount            = 0;
  private openFrameCount             = 0;
  private firstBlinkCompletedMs: number | null = null;
  private blinkCount                 = 0;

  // ── Shared cooldown (all gestures respect this) ──
  private cooldownUntil = 0;

  // ── Single-eye blink detectors ──
  private leftBlinkDetector  = new OnsetDetector();
  private rightBlinkDetector = new OnsetDetector();

  // ── Eyebrow detectors ──
  private browDualDetector  = new OnsetDetector();
  private browLeftDetector  = new OnsetDetector();
  private browRightDetector = new OnsetDetector();

  constructor(
    private readonly onFrame:   (f: GestureFrame) => void,
    private readonly onGesture: (tipoGestoId: string) => void,
  ) {}

  async init(): Promise<void> {
    console.log('[PocGestureDetector] init — loading WASM from', WASM_PATH);
    let fs;
    try {
      fs = await FilesetResolver.forVisionTasks(WASM_PATH);
    } catch (e) {
      console.error('[PocGestureDetector] FilesetResolver failed:', e);
      throw e;
    }

    this.landmarker = await FaceLandmarker.createFromOptions(fs, {
      baseOptions: {
        modelAssetPath:
          'https://storage.googleapis.com/mediapipe-models/face_landmarker/face_landmarker/float16/1/face_landmarker.task',
        delegate: 'CPU',
      },
      runningMode:           'VIDEO',
      numFaces:              1,
      outputFaceBlendshapes: true,
    });
    console.log('[PocGestureDetector] FaceLandmarker ready');
  }

  start(video: HTMLVideoElement, canvas: HTMLCanvasElement): void {
    this.active = true;
    this.resetAll();
    this.tick(video, canvas);
  }

  stop(): void {
    this.active = false;
    if (this.rafId !== null) { cancelAnimationFrame(this.rafId); this.rafId = null; }
  }

  destroy(): void {
    this.stop();
    this.landmarker?.close();
    this.landmarker = null;
  }

  // ---------------------------------------------------------------------------

  private resetAll(): void {
    this.blinkState            = 'WAITING_BLINK1';
    this.blinkFrameCount       = 0;
    this.openFrameCount        = 0;
    this.firstBlinkCompletedMs = null;
    this.blinkCount            = 0;
    this.cooldownUntil         = 0;   // sempre limpa ao reiniciar — sem bloqueio residual entre testes
    this.leftBlinkDetector.reset();
    this.rightBlinkDetector.reset();
    this.browDualDetector.reset();
    this.browLeftDetector.reset();
    this.browRightDetector.reset();
  }

  private fire(tipoGestoId: string, now: number): void {
    this.cooldownUntil = now + COOLDOWN_MS;
    this.resetAll();
    this.onGesture(tipoGestoId);
    // Re-enable after cooldown
    setTimeout(() => { this.blinkState = 'WAITING_BLINK1'; }, COOLDOWN_MS);
  }

  // ---------------------------------------------------------------------------

  private tick(video: HTMLVideoElement, canvas: HTMLCanvasElement): void {
    if (!this.active || !this.landmarker) return;

    const now = performance.now();
    const ctx = canvas.getContext('2d')!;
    ctx.clearRect(0, 0, canvas.width, canvas.height);

    if (video.readyState >= 2 && !video.paused) {
      let result;
      try {
        result = this.landmarker.detectForVideo(video, now);
      } catch (e) {
        console.warn('[PocGestureDetector] detectForVideo error:', e);
        this.rafId = requestAnimationFrame(() => this.tick(video, canvas));
        return;
      }

      const face = result.faceLandmarks?.[0];

      if (face) {
        this.drawGrid(ctx, canvas.width, canvas.height, face);

        const earL   = this.calcEar(face, LEFT_EYE_IDX);
        const earR   = this.calcEar(face, RIGHT_EYE_IDX);
        const earAvg = (earL + earR) / 2;

        // Extract blendshape scores
        const bs = result.faceBlendshapes?.[0]?.categories;
        const score = (name: string) => bs?.find(c => c.categoryName === name)?.score ?? 0;
        const eyeBlinkL      = score('eyeBlinkLeft');
        const eyeBlinkR      = score('eyeBlinkRight');
        const browInnerUp    = score('browInnerUp');
        const browOuterUpL   = score('browOuterUpLeft');
        const browOuterUpR   = score('browOuterUpRight');

        if (now >= this.cooldownUntil) {
          // Double blink (EAR-based — proven reliable)
          this.processDoubleBlink(earAvg, now);

          // Single left eye blink (blendshape-based)
          const leftOnly = eyeBlinkL > BLINK_BS_ON && eyeBlinkR < EYE_OTHER_MAX;
          if (this.leftBlinkDetector.process(leftOnly, SINGLE_BLINK_FRAMES)) {
            this.fire('tg-piscar-esquerdo', now);
          }

          // Single right eye blink
          const rightOnly = eyeBlinkR > BLINK_BS_ON && eyeBlinkL < EYE_OTHER_MAX;
          if (this.rightBlinkDetector.process(rightOnly, SINGLE_BLINK_FRAMES)) {
            this.fire('tg-piscar-direito', now);
          }

          // Both eyebrows raised simultaneously
          const bothBrow = browInnerUp > BROW_ON;
          if (this.browDualDetector.process(bothBrow, BROW_MIN_FRAMES)) {
            this.fire('tg-sobrancelha-dupla', now);
          }

          // Left eyebrow only (left outer up, right not raised much)
          const leftBrowOnly = browOuterUpL > BROW_ON && browOuterUpR < BROW_OFF;
          if (this.browLeftDetector.process(leftBrowOnly, BROW_MIN_FRAMES)) {
            this.fire('tg-sobrancelha-esquerda', now);
          }

          // Right eyebrow only
          const rightBrowOnly = browOuterUpR > BROW_ON && browOuterUpL < BROW_OFF;
          if (this.browRightDetector.process(rightBrowOnly, BROW_MIN_FRAMES)) {
            this.fire('tg-sobrancelha-direita', now);
          }
        }

        this.onFrame({ faceDetected: true, earAvg, earLeft: earL, earRight: earR, blinkCount: this.blinkCount });
      } else {
        this.onFrame({ faceDetected: false, earAvg: 0, earLeft: 0, earRight: 0, blinkCount: 0 });
      }
    } else {
      this.onFrame({ faceDetected: false, earAvg: 0, earLeft: 0, earRight: 0, blinkCount: 0 });
    }

    this.rafId = requestAnimationFrame(() => this.tick(video, canvas));
  }

  // ---------------------------------------------------------------------------
  // Double-blink state machine (EAR-based)
  // ---------------------------------------------------------------------------
  private processDoubleBlink(earAvg: number, now: number): void {
    const eyeClosed = earAvg < EAR_CLOSE;
    const eyeOpen   = earAvg >= EAR_OPEN;

    if (
      (this.blinkState === 'WAITING_BLINK2') &&
      this.firstBlinkCompletedMs !== null &&
      now - this.firstBlinkCompletedMs > DOUBLE_BLINK_WINDOW_MS
    ) {
      this.blinkState            = 'WAITING_BLINK1';
      this.blinkFrameCount       = 0;
      this.openFrameCount        = 0;
      this.firstBlinkCompletedMs = null;
      this.blinkCount            = 0;
    }

    switch (this.blinkState) {
      case 'WAITING_BLINK1':
        if (eyeClosed) { this.blinkState = 'IN_BLINK1'; this.blinkFrameCount = 1; }
        break;

      case 'IN_BLINK1':
        if (eyeClosed) {
          this.blinkFrameCount++;
        } else if (eyeOpen) {
          if (this.blinkFrameCount >= BLINK_MIN_FRAMES) {
            this.blinkState            = 'WAITING_OPEN1';
            this.openFrameCount        = 1;
            this.firstBlinkCompletedMs = now;
            this.blinkCount            = 1;
          } else {
            this.blinkState      = 'WAITING_BLINK1';
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
        if (eyeClosed) { this.blinkState = 'IN_BLINK2'; this.blinkFrameCount = 1; }
        break;

      case 'IN_BLINK2':
        if (eyeClosed) {
          this.blinkFrameCount++;
        } else if (eyeOpen) {
          if (
            this.blinkFrameCount >= BLINK_MIN_FRAMES &&
            this.firstBlinkCompletedMs !== null &&
            now - this.firstBlinkCompletedMs <= DOUBLE_BLINK_WINDOW_MS
          ) {
            this.blinkState  = 'DONE';
            this.blinkCount  = 2;
            this.fire('tg-piscar-duplo-001', now);
          } else {
            this.blinkState      = 'WAITING_BLINK1';
            this.blinkFrameCount = 0;
          }
        }
        break;

      case 'DONE':
        break;
    }
  }

  // ---------------------------------------------------------------------------

  private calcEar(lm: Array<{ x: number; y: number }>, idx: number[]): number {
    const p = idx.map(i => lm[i]);
    const d = (a: typeof p[0], b: typeof p[0]) => Math.hypot(a.x - b.x, a.y - b.y);
    return (d(p[1], p[5]) + d(p[2], p[4])) / (2 * d(p[0], p[3]));
  }

  // ── Canvas drawing ──────────────────────────────────────────────────────────

  private drawGrid(
    ctx: CanvasRenderingContext2D,
    W: number, H: number,
    lm: Array<{ x: number; y: number }>,
  ): void {
    ctx.lineWidth   = 0.5;
    ctx.strokeStyle = 'rgba(80, 200, 255, 0.13)';
    for (const c of FaceLandmarker.FACE_LANDMARKS_TESSELATION) {
      const a = lm[c.start], b = lm[c.end];
      ctx.beginPath(); ctx.moveTo(a.x * W, a.y * H); ctx.lineTo(b.x * W, b.y * H); ctx.stroke();
    }

    ctx.lineWidth   = 1;
    ctx.strokeStyle = 'rgba(80, 200, 255, 0.45)';
    for (const c of FaceLandmarker.FACE_LANDMARKS_FACE_OVAL) {
      const a = lm[c.start], b = lm[c.end];
      ctx.beginPath(); ctx.moveTo(a.x * W, a.y * H); ctx.lineTo(b.x * W, b.y * H); ctx.stroke();
    }

    ctx.lineWidth   = 2;
    ctx.strokeStyle = 'rgba(0, 255, 180, 0.92)';
    for (const c of [
      ...FaceLandmarker.FACE_LANDMARKS_LEFT_EYE,
      ...FaceLandmarker.FACE_LANDMARKS_RIGHT_EYE,
      ...FaceLandmarker.FACE_LANDMARKS_LEFT_EYEBROW,
      ...FaceLandmarker.FACE_LANDMARKS_RIGHT_EYEBROW,
    ]) {
      const a = lm[c.start], b = lm[c.end];
      ctx.beginPath(); ctx.moveTo(a.x * W, a.y * H); ctx.lineTo(b.x * W, b.y * H); ctx.stroke();
    }

    ctx.fillStyle = 'rgba(0, 255, 180, 0.9)';
    for (const idx of [...LEFT_EYE_IDX, ...RIGHT_EYE_IDX]) {
      const p = lm[idx];
      ctx.beginPath(); ctx.arc(p.x * W, p.y * H, 2.5, 0, Math.PI * 2); ctx.fill();
    }
  }
}
