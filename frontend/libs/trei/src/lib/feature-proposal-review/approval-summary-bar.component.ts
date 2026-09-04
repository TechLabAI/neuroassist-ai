import {
  ChangeDetectionStrategy,
  Component,
  Input,
} from '@angular/core';

// =============================================================================
// ApprovalSummaryBarComponent — SPEC-02 §3 (FE-TREI-020)
//
// Presentational: bar showing counts of nodes awaiting caregiver approval
// and those awaiting patient confirmation.
// Inputs only — no business logic.
// =============================================================================

@Component({
  selector: 'trei-approval-summary-bar',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="flex flex-wrap gap-4 rounded-xl bg-surface-2 px-4 py-3 text-sm"
      data-testid="approval-summary-bar"
      aria-label="Resumo de aprovações">
      <span class="text-ink-soft">
        Rascunhos:
        <span
          class="font-semibold text-ink"
          data-testid="rascunho-count"
          [attr.aria-label]="rascunhoCount + ' rascunhos'">
          {{ rascunhoCount }}
        </span>
      </span>
      <span class="h-4 w-px bg-line self-center" aria-hidden="true"></span>
      <span class="text-ink-soft">
        Aguardando paciente:
        <span
          class="font-semibold text-ink"
          data-testid="aguardando-count"
          [attr.aria-label]="aguardandoCount + ' aguardando paciente'">
          {{ aguardandoCount }}
        </span>
      </span>
    </div>
  `,
})
export class ApprovalSummaryBarComponent {
  /** Number of nodes still in rascunho (awaiting caregiver approval). */
  @Input() rascunhoCount = 0;

  /** Number of nodes approved by caregiver but awaiting patient confirmation. */
  @Input() aguardandoCount = 0;
}
