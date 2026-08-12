import {
  Component,
  ChangeDetectionStrategy,
  input,
  output,
  inject,
  effect,
  viewChild,
  ElementRef,
  OnDestroy,
} from '@angular/core';
import { ComandoDraft, GestureTestOutcome } from '@neuroassist/shared/contracts';
import { GestureTestFacade } from '../gest.facade';

// ---------------------------------------------------------------------------
// GestureLiveTestComponent — GEST-FE-011 (smart container)
// Composed into PAC's [slot=live-test] and the onboarding gesto step via
// GestShellIntegrationService.resolveLiveTestSlot() — never imported directly
// by scope:pac/scope:acs (GEST-INT-002 nx-boundary).
//
// Consolidates the Wave-3 presentational pieces (preview frame, state badge,
// composite step indicator, degraded banner, controls) inline for now —
// pragmatic-first; splitting into separate components (GEST-FE-012..016) is a
// non-breaking follow-up since this component's @Input/@Output surface won't
// change.
// ---------------------------------------------------------------------------

@Component({
  selector: 'gest-gesture-live-test',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="gest-live-test rounded-lg border border-line p-4">
      <!-- Degraded banner — always rendered (honest-degraded invariant, ADR-005) -->
      @if (facade.degraded()) {
        <div class="mb-3 rounded-md bg-orange-50 px-3 py-2 text-xs text-orange-800">
          {{ degradedCopy() }}
        </div>
      }

      <!-- Composite step indicator -->
      @if (draft().composite && facade.testState() === 'testing') {
        <div class="mb-3 flex items-center gap-2 text-xs text-ink-soft">
          <span [class.font-bold]="facade.step() === 'principal'">1. Gesto principal</span>
          <span>→</span>
          <span [class.font-bold]="facade.step() === 'secundario'">2. Gesto secundário</span>
        </div>
      }

      <!-- Preview frame (cosmetic only — real detection runs on the edge agent) -->
      @if (facade.testState() === 'testing' || facade.testState() === 'recognized') {
        <video
          #previewVideo
          class="mb-3 w-full max-w-sm rounded-md bg-black"
          autoplay
          muted
          playsinline
        ></video>
      }

      <!-- State badge -->
      <div
        class="mb-3 inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium"
        [class]="badgeClass()"
      >
        {{ facade.message() }}
      </div>

      <!-- Controls -->
      <div class="flex items-center gap-2">
        @if (facade.testState() === 'idle' || facade.testState() === 'not-recognized' || facade.testState() === 'camera-error') {
          <button
            type="button"
            class="rounded-md bg-brand px-3 py-1.5 text-xs font-semibold text-white"
            (click)="start()"
          >
            {{ facade.testState() === 'idle' ? 'Testar gesto' : 'Tentar novamente' }}
          </button>
        }
        @if (facade.testState() === 'testing') {
          <button
            type="button"
            class="rounded-md border border-line px-3 py-1.5 text-xs font-medium text-ink"
            (click)="stop()"
          >
            Parar
          </button>
        }
      </div>
    </div>
  `,
})
export class GestureLiveTestComponent implements OnDestroy {
  protected readonly facade = inject(GestureTestFacade);

  readonly draft = input.required<ComandoDraft>();
  readonly outcome = output<GestureTestOutcome>();

  private readonly previewVideo = viewChild<ElementRef<HTMLVideoElement>>('previewVideo');

  constructor() {
    // Bind the cosmetic preview stream to the <video> element whenever both
    // become available (view child only exists while state is testing/recognized).
    effect(() => {
      const video = this.previewVideo()?.nativeElement;
      const stream = this.facade.previewStream();
      if (video && stream && video.srcObject !== stream) {
        video.srcObject = stream;
      }
    });

    // Forward completed outcomes to the host page (PAC command editor).
    effect(() => {
      const outcome = this.facade.lastOutcome();
      if (outcome && outcome.commandDraftRef === this.draft().commandDraftRef) {
        this.outcome.emit(outcome);
      }
    });
  }

  protected start(): void {
    this.facade.startTest(this.draft());
  }

  protected stop(): void {
    this.facade.stopTest();
  }

  protected badgeClass(): string {
    switch (this.facade.testState()) {
      case 'recognized':
        return 'bg-green-100 text-green-800';
      case 'not-recognized':
      case 'camera-error':
        return 'bg-red-100 text-red-800';
      case 'testing':
        return 'bg-blue-100 text-blue-800';
      default:
        return 'bg-surface-2 text-ink-soft';
    }
  }

  protected degradedCopy(): string {
    switch (this.facade.state().degradedReason) {
      case 'device-offline':
        return 'Dispositivo offline — teste em modo simulado, resultado não é real.';
      case 'bridge-unreachable':
        return 'Agente de detecção local não respondeu — teste em modo simulado.';
      case 'version-mismatch':
        return 'Versão do agente de detecção incompatível — teste em modo simulado.';
      default:
        return 'Modo degradado — resultado do teste é simulado, não real.';
    }
  }

  ngOnDestroy(): void {
    this.facade.stopTest();
  }
}
