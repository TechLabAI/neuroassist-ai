import {
  ChangeDetectionStrategy,
  Component,
  computed,
  input,
} from '@angular/core';
import { NgClass } from '@angular/common';
import type { SessionStatus, NodeSubEstado } from '../trei.types';

// =============================================================================
// StatusChipComponent — SPEC-06 §4 (TREI-FE-021)
//
// Reutilizável — aceita SessionStatus ou NodeSubEstado e renderiza um chip
// de status com label legível para a cuidadora.
//
// Regras de cor: ZERO valores hardcoded. Todas as cores são CSS custom
// properties provenientes de @neuroassist/shared/ui-tokens (ou definidas via
// host-element :host vars como fallback enquanto o design system está em stub).
//
// Usage:
//   <trei-status-chip [status]="vm.status" />
//   <trei-status-chip [status]="node.subEstado" />
// =============================================================================

export type ChipStatus = SessionStatus | NodeSubEstado;

interface ChipConfig {
  label: string;
  /** CSS class applied to set --chip-color vars (defined in :host styles). */
  variant: 'active' | 'ended' | 'error' | 'pending' | 'approved' | 'published' | 'rejected';
}

const CHIP_MAP: Record<ChipStatus, ChipConfig> = {
  // SessionStatus
  ACTIVE: { label: 'Ativa', variant: 'active' },
  ENDED: { label: 'Encerrada', variant: 'ended' },
  ERROR: { label: 'Erro', variant: 'error' },
  // NodeSubEstado
  PENDING: { label: 'Pendente', variant: 'pending' },
  APPROVED_CAREGIVER: { label: 'Aprovado (cuidadora)', variant: 'approved' },
  APPROVED_PATIENT: { label: 'Aprovado (paciente)', variant: 'approved' },
  PUBLISHED: { label: 'Publicado', variant: 'published' },
  REJECTED: { label: 'Rejeitado', variant: 'rejected' },
} as const;

@Component({
  selector: 'trei-status-chip',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [NgClass],
  styles: [`
    :host {
      display: inline-flex;
    }

    /* -----------------------------------------------------------------------
     * Variant-to-token map — uses CSS custom properties so that
     * @neuroassist/shared/ui-tokens can override them globally once the
     * design-system token set is fleshed out (D-328).
     * Until then each variant carries safe fallback values.
     * ----------------------------------------------------------------------- */
    .chip {
      display: inline-flex;
      align-items: center;
      gap: 0.25rem;
      border-radius: 9999px;
      padding: 0.125rem 0.625rem;
      font-size: 0.75rem;
      font-weight: 600;
      line-height: 1.25rem;
      background-color: var(--chip-bg);
      color: var(--chip-ink);
      border: 1px solid var(--chip-border);
    }

    .chip--active {
      --chip-bg:     var(--color-success-bg,   #dcfce7);
      --chip-ink:    var(--color-success-ink,   #166534);
      --chip-border: var(--color-success,       #22c55e);
    }
    .chip--ended {
      --chip-bg:     var(--color-neutral-bg,    #f1f5f9);
      --chip-ink:    var(--color-neutral-ink,   #475569);
      --chip-border: var(--color-neutral,       #94a3b8);
    }
    .chip--error {
      --chip-bg:     var(--color-danger-bg,     #fee2e2);
      --chip-ink:    var(--color-danger-ink,    #991b1b);
      --chip-border: var(--color-danger,        #ef4444);
    }
    .chip--pending {
      --chip-bg:     var(--color-warning-bg,    #fef9c3);
      --chip-ink:    var(--color-warning-ink,   #854d0e);
      --chip-border: var(--color-warning,       #eab308);
    }
    .chip--approved {
      --chip-bg:     var(--color-primary-bg,    #eff6ff);
      --chip-ink:    var(--color-primary-ink,   #1e40af);
      --chip-border: var(--color-primary,       #3b82f6);
    }
    .chip--published {
      --chip-bg:     var(--color-success-bg,   #dcfce7);
      --chip-ink:    var(--color-success-ink,   #166534);
      --chip-border: var(--color-success,       #22c55e);
    }
    .chip--rejected {
      --chip-bg:     var(--color-danger-bg,     #fee2e2);
      --chip-ink:    var(--color-danger-ink,    #991b1b);
      --chip-border: var(--color-danger,        #ef4444);
    }
  `],
  template: `
    <span
      class="chip"
      [ngClass]="chipClass()"
      [attr.data-status]="status()"
      [attr.aria-label]="config().label">
      {{ config().label }}
    </span>
  `,
})
export class StatusChipComponent {
  readonly status = input.required<ChipStatus>();

  protected readonly config = computed<ChipConfig>(
    () => CHIP_MAP[this.status()] ?? { label: this.status(), variant: 'ended' },
  );

  protected readonly chipClass = computed(
    () => `chip--${this.config().variant}`,
  );
}
