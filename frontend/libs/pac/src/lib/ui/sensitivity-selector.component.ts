import { Component, ChangeDetectionStrategy, Input, Output, EventEmitter } from '@angular/core';

// ---------------------------------------------------------------------------
// SensitivitySelectorComponent — PAC-FE-019 (P7 UX Polish)
// Display-only (readonly=true) or interactive (readonly=false) component for
// command sensitivity. SPEC-03 §1, SPEC-05 constraint: NEVER renders threshold
// numeric values — only PT-BR labels ('Baixa' | 'Média' | 'Alta').
//
// Modes:
//   - readonly=true  (default): renders a static label chip.
//   - readonly=false: renders three clickable buttons; emits 'change' on click.
//
// NOTE: Uses @Input() decorator (not input() signal) because ts-jest JIT
// compilation does not support setInput() for signal inputs (known limitation).
//
// Pure @Input/@Output — zero facade access.
// ---------------------------------------------------------------------------

export type SensibilidadeType = 'baixa' | 'media' | 'alta';

export const SENSIBILIDADE_LABELS: Record<SensibilidadeType, string> = {
  baixa: 'Baixa',
  media: 'Média',
  alta: 'Alta',
};

const OPTIONS: { value: SensibilidadeType; label: string }[] = [
  { value: 'baixa', label: 'Baixa' },
  { value: 'media', label: 'Média' },
  { value: 'alta', label: 'Alta' },
];

@Component({
  selector: 'pac-sensitivity-selector',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (readonly) {
      <span class="inline-block rounded px-2 py-0.5 text-xs font-medium bg-surface-2 text-ink-soft">
        {{ LABELS[sensibilidade] }}
      </span>
    } @else {
      <div class="flex gap-1">
        @for (opt of options; track opt.value) {
          <button
            type="button"
            class="rounded px-2 py-0.5 text-xs font-medium transition-colors border"
            [class]="sensibilidade === opt.value
              ? 'bg-brand-subtle text-brand border-brand'
              : 'bg-surface-2 text-ink-soft hover:bg-surface border-line'"
            (click)="onChange(opt.value)">
            {{ opt.label }}
          </button>
        }
      </div>
    }
  `,
})
export class SensitivitySelectorComponent {
  /** Defaults to 'media' (balanced) so the component renders safely. */
  @Input() sensibilidade: SensibilidadeType = 'media';
  /** true (default) → display-only chip; false → clickable selector */
  @Input() readonly = true;
  /** Emits the selected SensibilidadeType when a button is clicked (interactive mode only). */
  @Output() selectionChange = new EventEmitter<SensibilidadeType>();

  protected readonly LABELS = SENSIBILIDADE_LABELS;
  protected readonly options = OPTIONS;

  protected onChange(value: SensibilidadeType): void {
    this.selectionChange.emit(value);
  }
}
