import {
  Component,
  ChangeDetectionStrategy,
  Input,
  computed,
  signal,
} from '@angular/core';
import { PairingPhase } from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// PairingProgressComponent — FE-EDGE-010
// Presentational: step indicator for the pairing wizard.
// Renders the 4 sequential phases (idle is pre-wizard; expired/error are exits).
//
// NOTE: @Input() is used instead of the input() signal API (ts-jest limitation).
//
// Standalone; OnPush; Signals-first; no facade dependency.
// ---------------------------------------------------------------------------

interface WizardStep {
  phase: PairingPhase;
  label: string;
}

const WIZARD_STEPS: WizardStep[] = [
  { phase: 'registering',              label: 'Solicitar código'    },
  { phase: 'awaiting-claim',           label: 'Aguardar agente'     },
  { phase: 'awaiting-first-heartbeat', label: 'Primeiro heartbeat'  },
  { phase: 'paired',                   label: 'Pareado'             },
];

const PHASE_ORDER: PairingPhase[] = [
  'idle',
  'registering',
  'awaiting-claim',
  'awaiting-first-heartbeat',
  'paired',
];

@Component({
  selector: 'edge-pairing-progress',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <ol class="flex items-center gap-0 text-xs">
      @for (step of steps; track step.phase; let last = $last) {
        <li class="flex items-center">
          <!-- Step bubble -->
          <span
            class="flex h-6 w-6 items-center justify-center rounded-full text-[10px] font-bold"
            [class.bg-brand]="isCompleted($index) || isActive($index)"
            [class.text-brand-ink]="isCompleted($index) || isActive($index)"
            [class.bg-surface-2]="!isCompleted($index) && !isActive($index)"
            [class.text-ink-faint]="!isCompleted($index) && !isActive($index)"
            [class.ring-2]="isActive($index)"
            [class.ring-brand]="isActive($index)"
            [attr.aria-current]="isActive($index) ? 'step' : null">
            @if (isCompleted($index)) {
              <span class="material-symbols-outlined text-[12px]">check</span>
            } @else {
              {{ $index + 1 }}
            }
          </span>

          <!-- Step label (hidden on small screens) -->
          <span
            class="ml-1.5 hidden sm:inline"
            [class.font-semibold]="isActive($index)"
            [class.text-brand]="isActive($index)"
            [class.text-ink-faint]="!isCompleted($index) && !isActive($index)"
            [class.text-ink-soft]="isCompleted($index) && !isActive($index)">
            {{ step.label }}
          </span>

          <!-- Connector -->
          @if (!last) {
            <span class="mx-2 h-px w-8 flex-shrink-0"
                  [class.bg-brand]="isCompleted($index)"
                  [class.bg-line]="!isCompleted($index)"></span>
          }
        </li>
      }
    </ol>
  `,
})
export class PairingProgressComponent {
  @Input({ required: true }) set phase(value: PairingPhase) {
    this._phase.set(value);
  }

  readonly steps = WIZARD_STEPS;

  protected readonly _phase = signal<PairingPhase>('idle');

  /** Index of the active step (wizard step index, not full phase index). */
  readonly activeStepIndex = computed<number>(() => {
    const phaseIdx = PHASE_ORDER.indexOf(this._phase());
    // Map phase index to wizard step index (idle=-1, registering=0, …)
    return phaseIdx - 1; // WIZARD_STEPS starts from 'registering'
  });

  isCompleted(stepIdx: number): boolean {
    return stepIdx < this.activeStepIndex();
  }

  isActive(stepIdx: number): boolean {
    return stepIdx === this.activeStepIndex();
  }
}
