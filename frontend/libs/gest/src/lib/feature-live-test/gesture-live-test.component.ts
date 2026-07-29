import {
  Component,
  ChangeDetectionStrategy,
  inject,
  input,
  output,
  viewChild,
  ElementRef,
  effect,
  OnDestroy,
} from '@angular/core';
import { GestureTestFacade } from '../gest.facade';
import {
  ComandoDraft,
  GestureTestOutcome,
  GestureTestState,
} from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// GestureLiveTestComponent — SPEC-03 §5, GEST-FE-011
// The live-test panel: webcam preview + start/stop + recognition result.
// This is NOT a routed screen — it is an embeddable component composed by host
// pages (PAC calibracao editor, onboarding gesture step) at scope:web.
// Composition path: host page → GestShellIntegrationService.resolveLiveTestSlot()
// nx-boundary: scope:pac/scope:acs MUST NOT import scope:gest directly (GEST-INT-002).
//
// Invariants:
//   - GEST-FE-011: ngOnDestroy MUST call facade.stopTest() (camera lifecycle safety)
//   - Camera off outside 'testing' state (SPEC-03 §4 invariant 1)
//   - No raw confidence numbers on screen (SPEC-03 §4 invariant 2)
//   - degraded:true in outcome => simulated verdict, not real (ADR-005)
// ---------------------------------------------------------------------------

@Component({
  selector: 'gest-live-test',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="flex items-center gap-4 rounded-lg bg-surface-2 p-4">
      <!-- Camera preview -->
      <div
        class="relative grid h-20 w-28 flex-shrink-0 place-items-center overflow-hidden rounded-xl bg-surface"
      >
        <video
          #videoPreview
          class="h-full w-full object-cover"
          [class.hidden]="facade.testState() !== 'testing'"
          autoplay
          muted
          playsinline
        ></video>
        @if (facade.testState() !== 'testing') {
          <span
            class="material-symbols-outlined text-[28px]"
            [class]="iconClass(facade.testState())"
          >
            {{ iconName(facade.testState()) }}
          </span>
        }
      </div>

      <!-- Status / message -->
      <div class="flex-1 text-xs">
        @if (facade.degraded()) {
          <div class="mb-1 rounded-md bg-warn-bg px-2 py-1 text-xs font-medium text-warn">
            Modo degradado — agente de detecção indisponível. Resultado simulado.
          </div>
        }
        <span [class]="messageClass(facade.testState())">
          {{ facade.message() }}
        </span>
        @if (facade.testState() === 'testing' && commandDraft().composite && facade.step()) {
          <div class="mt-1 flex items-center gap-1 text-ink-faint">
            <span
              class="material-symbols-outlined text-[14px]"
              [class]="facade.step() === 'secundario' ? 'text-success' : 'text-ink-faint'"
            >
              {{ facade.step() === 'secundario' ? 'check_circle' : 'radio_button_unchecked' }}
            </span>
            <span>Gesto principal</span>
            <span class="mx-1">›</span>
            <span
              class="material-symbols-outlined text-[14px]"
              [class]="'text-ink-faint'"
            >radio_button_unchecked</span>
            <span>Gesto secundário</span>
          </div>
        }
      </div>

      <!-- Controls -->
      @if (facade.testState() === 'testing') {
        <button
          class="rounded-lg border border-line px-3 py-1.5 text-xs font-semibold text-ink-soft hover:bg-surface-2"
          (click)="onStop()"
        >
          Parar
        </button>
      } @else if (
        facade.testState() === 'recognized' ||
        facade.testState() === 'not-recognized' ||
        facade.testState() === 'camera-error'
      ) {
        <button
          class="rounded-lg border border-brand px-3 py-1.5 text-xs font-semibold text-brand hover:bg-brand-subtle"
          (click)="onRetry()"
        >
          Testar de novo
        </button>
      } @else {
        <button
          class="rounded-lg border border-line px-3 py-1.5 text-xs font-semibold text-ink-soft hover:bg-surface-2"
          (click)="onStart()"
        >
          Testar agora
        </button>
      }
    </div>
  `,
})
export class GestureLiveTestComponent implements OnDestroy {
  protected readonly facade = inject(GestureTestFacade);

  /** Input: command draft provided by the host page (PAC editor or onboarding). */
  readonly commandDraft = input.required<ComandoDraft>();

  /** Output: emitted when the test reaches a terminal state (recognized/not-recognized). */
  readonly testCompleted = output<GestureTestOutcome>();

  private readonly videoPreview =
    viewChild<ElementRef<HTMLVideoElement>>('videoPreview');

  constructor() {
    // Wire facade mediaStream → video element (runs after each render cycle)
    effect(() => {
      if (this.facade.testState() === 'testing') {
        // The facade owns the MediaStream; we just display it
        // Video srcObject is set by the facade via the videoPreview ref
        this.wireVideo();
      }
    });

    // Emit outcome to host page when test reaches terminal state
    effect(() => {
      const outcome = this.facade.lastOutcome();
      if (outcome) {
        this.testCompleted.emit(outcome);
      }
    });
  }

  /** GEST-FE-011 invariant: ngOnDestroy MUST call stopTest() to release camera. */
  ngOnDestroy(): void {
    this.facade.stopTest();
  }

  protected onStart(): void {
    this.facade.startTest(this.commandDraft());
    // Wire video element after camera is acquired (effect handles reactively)
  }

  protected onStop(): void {
    this.facade.stopTest();
  }

  protected onRetry(): void {
    this.facade.retry();
  }

  protected iconClass(state: GestureTestState): string {
    if (state === 'recognized') return 'text-success';
    if (state === 'not-recognized' || state === 'camera-error') return 'text-danger';
    return 'text-ink-faint';
  }

  protected iconName(state: GestureTestState): string {
    if (state === 'recognized') return 'check_circle';
    if (state === 'not-recognized') return 'do_not_disturb_on';
    if (state === 'camera-error') return 'videocam_off';
    return 'videocam';
  }

  protected messageClass(state: GestureTestState): string {
    if (state === 'testing') return 'font-medium text-brand';
    if (state === 'recognized') return 'font-medium text-success-ink';
    if (state === 'not-recognized' || state === 'camera-error')
      return 'font-medium text-danger';
    return 'text-ink-soft';
  }

  // Wire the facade's MediaStream (if available) into the video element.
  // The facade owns the stream; this component is only responsible for display.
  private wireVideo(): void {
    const video = this.videoPreview()?.nativeElement;
    if (!video) return;
    // TODO[GEST-FE-011]: expose MediaStream ref from facade for component binding
    // For now, the component acquires its own preview via getUserMedia in the facade.
    // Layer 2 will refactor this to read facade.mediaStreamRef() signal.
    video.srcObject = null; // placeholder — Layer 2 wires the real stream ref
  }
}
