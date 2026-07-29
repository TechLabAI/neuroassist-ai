import {
  Injectable,
  inject,
  signal,
  computed,
  DestroyRef,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription } from 'rxjs';
import { EDGE_DETECTION_PORT } from './api/edge-detection.port';
import {
  EdgeRecognitionSignal,
  ComandoDraft,
  GestureTestVM,
  GestureTestState,
  GestureTestStep,
  DegradedReason,
  GestureTestOutcome,
} from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// GestureTestFacade — SPEC-04 §3
// Single owner of the MediaStream and edge bridge subscription.
// Signals-first: components read .asReadonly() signals, never mutate state.
// ---------------------------------------------------------------------------

const NEGOTIATED_VERSION = '1.0.0';

function initialVm(): GestureTestVM {
  return {
    commandDraftRef: '',
    rotulo: '',
    state: 'idle',
    step: null,
    composite: false,
    message: 'Faça o gesto em frente à webcam para confirmar que está sendo reconhecido.',
    deviceOnline: true, // optimistic; updated by gestDeviceOnlineGuard
    degraded: false,
    degradedReason: null,
    contractVersion: null,
  };
}

@Injectable({ providedIn: 'root' })
export class GestureTestFacade {
  private readonly port = inject(EDGE_DETECTION_PORT);
  private readonly destroyRef = inject(DestroyRef);

  private readonly _state = signal<GestureTestVM>(initialVm());
  private streamSub: Subscription | null = null;
  private mediaStream: MediaStream | null = null;
  private currentDraft: ComandoDraft | null = null;

  /** Read-only view of the full VM (SPEC-03 §2). */
  readonly state = this._state.asReadonly();

  /** Shortcut signals for component binding. */
  readonly step = computed(() => this._state().step);
  readonly deviceOnline = computed(() => this._state().deviceOnline);
  readonly degraded = computed(() => this._state().degraded);
  readonly testState = computed(() => this._state().state);
  readonly message = computed(() => this._state().message);

  /** Update device online status (called by gestDeviceOnlineGuard or shell). */
  setDeviceOnline(online: boolean, reason: DegradedReason = null): void {
    this._state.update((vm) => ({
      ...vm,
      deviceOnline: online,
      degraded: !online,
      degradedReason: online ? null : (reason ?? 'device-offline'),
    }));
  }

  /** Acquire getUserMedia + subscribe to edge bridge stream (real or mock).
   * Satisfies SPEC-04 §3 EARS: WHEN startTest dispatched, SHALL acquire MediaStream
   * AND subscribe to the edge recognition stream. */
  startTest(draft: ComandoDraft): void {
    this.stopTest(); // ensure clean state
    this.currentDraft = draft;

    this._state.set({
      commandDraftRef: draft.commandDraftRef,
      rotulo: draft.rotulo,
      state: 'testing',
      step: draft.composite ? 'principal' : null,
      composite: draft.composite,
      message: draft.composite
        ? 'Câmera ativa — faça o gesto principal agora…'
        : 'Câmera ativa — faça o gesto agora…',
      deviceOnline: this._state().deviceOnline,
      degraded: this._state().degraded,
      degradedReason: this._state().degradedReason,
      contractVersion: null,
    });

    // Acquire camera (privacy-by-default: only open on explicit user action)
    void this.acquireCamera().then((ok) => {
      if (!ok) return;
      this.subscribeToStream(draft);
    });
  }

  /** Stop the active test: unsubscribe, stop ALL MediaStream tracks, close bridge.
   * Satisfies SPEC-04 §3 EARS: WHEN stopTest dispatched, SHALL unsubscribe + stop tracks. */
  stopTest(): void {
    this.unsubscribeStream();
    this.releaseCamera();
    this.port.close();
    const current = this._state();
    if (current.state === 'testing') {
      this._state.update((vm) => ({ ...vm, state: 'idle', step: null }));
    }
  }

  /** Stop then restart with the same draft (camera-error or not-recognized recovery). */
  retry(): void {
    if (this.currentDraft) {
      this.startTest(this.currentDraft);
    }
  }

  // ---------------------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------------------

  private async acquireCamera(): Promise<boolean> {
    if (!navigator.mediaDevices?.getUserMedia) {
      this._state.update((vm) => ({
        ...vm,
        state: 'camera-error',
        message:
          'Não foi possível acessar a webcam. Verifique a permissão do navegador e tente novamente.',
      }));
      return false;
    }
    try {
      this.mediaStream = await navigator.mediaDevices.getUserMedia({
        video: true,
        audio: false,
      });
      return true;
    } catch {
      this._state.update((vm) => ({
        ...vm,
        state: 'camera-error',
        message:
          'Não foi possível acessar a webcam. Verifique a permissão do navegador e tente novamente.',
      }));
      return false;
    }
  }

  private releaseCamera(): void {
    this.mediaStream?.getTracks().forEach((t) => t.stop());
    this.mediaStream = null;
  }

  private subscribeToStream(draft: ComandoDraft): void {
    const stream$ = this._state().degraded
      ? this.port.mockStream(draft)
      : this.port.openStream(draft);

    const timeoutId = setTimeout(() => {
      // SPEC-04 EARS: WHEN no signal within tempoMaximoGestoMs → not-recognized
      if (this._state().state === 'testing') {
        this.finalize(false, draft);
      }
    }, draft.tempoMaximoGestoMs);

    this.streamSub = stream$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (signal) => this.handleSignal(signal, draft, timeoutId),
        error: () => {
          clearTimeout(timeoutId);
          this._state.update((vm) => ({
            ...vm,
            state: 'not-recognized',
            message:
              'Erro na conexão com o agente de detecção. Tente novamente.',
          }));
        },
        complete: () => clearTimeout(timeoutId),
      });
  }

  private unsubscribeStream(): void {
    this.streamSub?.unsubscribe();
    this.streamSub = null;
  }

  /** Map an EdgeRecognitionSignal to VM state (SPEC-04 §3 signal→VM table). */
  private handleSignal(
    sig: EdgeRecognitionSignal,
    draft: ComandoDraft,
    timeoutId: ReturnType<typeof setTimeout>,
  ): void {
    const vm = this._state();

    // Propagate degraded flag from signal
    const isDegraded = sig.degraded || vm.degraded;

    switch (sig.signalKind) {
      case 'preview_frame_ack':
        // liveness heartbeat — update contractVersion, stay in testing
        this._state.update((v) => ({
          ...v,
          contractVersion: sig.contractVersion,
          degraded: isDegraded,
        }));
        break;

      case 'recognized': {
        if (draft.composite && vm.step === 'principal') {
          // composite step 1 done — advance to secundario
          this._state.update((v) => ({
            ...v,
            step: 'secundario',
            message: 'Gesto principal reconhecido — agora faça o segundo gesto…',
            degraded: isDegraded,
          }));
        } else {
          // simple command OR composite step 2 done — finalize
          clearTimeout(timeoutId);
          this.finalize(true, draft, isDegraded);
        }
        break;
      }

      case 'not_recognized':
        clearTimeout(timeoutId);
        this.finalize(false, draft, isDegraded);
        break;

      case 'degraded':
        // Edge agent signaled degraded mode explicitly
        this._state.update((v) => ({
          ...v,
          degraded: true,
          degradedReason: v.degradedReason ?? 'bridge-unreachable',
          message:
            'Agente de detecção indisponível. Teste em modo simulado — resultado não é real.',
        }));
        break;
    }
  }

  private finalize(
    recognized: boolean,
    draft: ComandoDraft,
    isDegraded = false,
  ): void {
    // Honest-degraded invariant: if degraded:true, outcome.degraded must be true
    const finalDegraded = isDegraded || this._state().degraded;

    const outcome: GestureTestOutcome = {
      commandDraftRef: draft.commandDraftRef,
      recognized,
      degraded: finalDegraded,
      composite: draft.composite,
      completedStep: this._state().step,
    };

    this._state.update((vm) => ({
      ...vm,
      state: recognized ? 'recognized' : 'not-recognized',
      message: recognized
        ? finalDegraded
          ? 'Gesto simulado como reconhecido (modo degradado — sem agente de detecção real).'
          : 'Gesto reconhecido com sucesso!'
        : 'Não reconhecido — repita o gesto ou aumente a sensibilidade.',
      degraded: finalDegraded,
    }));

    this.releaseCamera();
    this.unsubscribeStream();
    // outcome is stored for host page polling — exposed via lastOutcome signal
    this._lastOutcome.set(outcome);
  }

  /** Last completed outcome; host page reads this to decide whether to enable Save. */
  private readonly _lastOutcome = signal<GestureTestOutcome | null>(null);
  readonly lastOutcome = this._lastOutcome.asReadonly();
}
