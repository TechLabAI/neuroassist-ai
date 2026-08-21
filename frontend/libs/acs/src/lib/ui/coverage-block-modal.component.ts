import {
  Component,
  ChangeDetectionStrategy,
  Input,
  Output,
  EventEmitter,
} from '@angular/core';

// =============================================================================
// CoverageBlockModalComponent — ADR-005 safety-critical blocking modal
//
// Shown when a coordinator attempts to:
//   (a) deactivate the last caregiver covering a patient, OR
//   (b) link a caregiver from a different empresa (cross-tenant)
//
// This modal is non-dismissible via backdrop click — user MUST click "Entendido".
// It is rendered as a fixed overlay with role="alertdialog" + aria-modal="true".
// The parent component controls visibility via facade.coverageModal() signal;
// dismissal is wired to facade.dismissCoverageModal() via the (confirmed) output.
// =============================================================================

@Component({
  selector: 'acs-coverage-block-modal',
  standalone: true,
  imports: [],
  template: `
    <!-- Fixed overlay — position: fixed; inset: 0 (Tailwind: inset-0) — no backdrop click -->
    <div
      class="fixed inset-0 z-50 flex items-center justify-center"
      role="alertdialog"
      aria-modal="true"
      [attr.aria-labelledby]="labelId"
      data-testid="coverage-block-modal"
    >
      <!-- Backdrop -->
      <div class="absolute inset-0 bg-black/50" aria-hidden="true"></div>

      <!-- Modal panel -->
      <div class="relative mx-4 w-full max-w-md rounded-2xl bg-surface p-6 shadow-xl">

        <!-- Icon + title -->
        <div class="mb-4 flex items-start gap-3">
          <span
            class="material-symbols-outlined mt-0.5 text-[24px] text-error"
            aria-hidden="true"
          >block</span>
          <div>
            <h2
              [id]="labelId"
              class="text-base font-bold text-ink"
            >Ação bloqueada</h2>
            <p class="mt-1 text-sm text-ink-soft">
              {{ message }}
            </p>
            @if (pacienteNome) {
              <p class="mt-2 text-xs text-ink-soft">
                Paciente referenciado: <span class="font-medium text-ink">{{ pacienteNome }}</span>
              </p>
            }
          </div>
        </div>

        <!-- CTA -->
        <div class="flex justify-end">
          <button
            class="rounded-xl bg-brand px-5 py-2.5 text-sm font-semibold text-brand-ink hover:bg-brand-strong focus:outline-none focus:ring-2 focus:ring-brand focus:ring-offset-2"
            (click)="confirmed.emit()"
            data-testid="coverage-modal-confirm-btn"
            aria-label="Entendido — fechar aviso de cobertura bloqueada"
          >
            Entendido
          </button>
        </div>
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CoverageBlockModalComponent {
  /**
   * Determines the message shown:
   * - 'deactivate': last-covering caregiver deactivation blocked (ADR-005)
   * - 'cross-tenant': link attempt across empresa boundaries blocked (ADR-005)
   */
  @Input() type: 'deactivate' | 'cross-tenant' = 'deactivate';

  /**
   * Patient name (or ID if name is unavailable) displayed as supplementary context.
   * May be empty — the message is self-contained without it.
   */
  @Input() pacienteNome = '';

  /**
   * Emitted when the coordinator acknowledges the block. The parent should call
   * facade.dismissCoverageModal() in response to clear the signal.
   */
  @Output() readonly confirmed = new EventEmitter<void>();

  protected readonly labelId = `coverage-modal-label-${Math.random().toString(36).slice(2, 7)}`;

  get message(): string {
    if (this.type === 'cross-tenant') {
      return 'Cuidador e paciente pertencem a empresas diferentes — o vínculo não é permitido.';
    }
    return 'Este paciente ficaria sem cuidador responsável — vincule ou ative outro antes.';
  }
}
