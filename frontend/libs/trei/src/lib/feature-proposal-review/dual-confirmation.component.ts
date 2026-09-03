import {
  ChangeDetectionStrategy,
  Component,
  Input,
} from '@angular/core';

// =============================================================================
// DualConfirmationComponent — SPEC-02 §3 / ADR-007 (FE-TREI-019)
//
// Presentational: renders the dual-approval indicator for a proposed node.
//
// INV-1 (ADR-007): TREI-FE owns only the caregiver side of approval.
//   - Input: caregiverApproved (the only writable side in TREI)
//   - Patient side: static "Aguardando paciente" — CONV (Tela 09) owns that side.
//   - confirmadoPaciente is intentionally absent from this component.
//   - No output that could trigger patient-side actions.
//
// The patient-side placeholder communicates to the caregiver that the phrase
// will reach the patient's runtime only after THEY confirm it in CONV.
// =============================================================================

@Component({
  selector: 'trei-dual-confirmation',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="flex shrink-0 flex-col gap-1 text-xs"
      data-testid="dual-confirmation"
      role="status"
      aria-label="Estado de aprovação">

      <!-- Caregiver approval (actionable in TREI — this is the TREI side, INV-1) -->
      <div
        class="flex items-center gap-1.5"
        [attr.data-testid]="'cuidadora-' + (caregiverApproved ? 'confirmed' : 'pending')"
        aria-label="Aprovação da cuidadora">
        @if (caregiverApproved) {
          <span
            class="material-symbols-outlined text-[14px] text-success-ink"
            aria-hidden="true">
            check_circle
          </span>
          <span class="font-semibold text-success-ink">Cuidadora ✓</span>
        } @else {
          <span
            class="material-symbols-outlined text-[14px] text-ink-faint"
            aria-hidden="true">
            radio_button_unchecked
          </span>
          <span class="text-ink-faint">Cuidadora pendente</span>
        }
      </div>

      <!-- Patient side — read-only placeholder (INV-1: CONV/Tela 09 owns this side) -->
      <!-- confirmadoPaciente is intentionally NOT referenced here -->
      <div
        class="flex items-center gap-1.5"
        data-testid="paciente-pending"
        aria-label="Aguardando confirmação do paciente no app do paciente">
        <span
          class="material-symbols-outlined text-[14px] text-ink-faint"
          aria-hidden="true">
          hourglass_empty
        </span>
        <span class="text-ink-faint">Aguardando paciente</span>
      </div>
    </div>
  `,
})
export class DualConfirmationComponent {
  /**
   * Whether the caregiver has confirmed this node.
   * INV-1: this is the ONLY approval side managed by TREI.
   * confirmadoPaciente is absent by design — CONV (Tela 09) owns that side.
   */
  @Input({ required: true }) caregiverApproved!: boolean;
}
