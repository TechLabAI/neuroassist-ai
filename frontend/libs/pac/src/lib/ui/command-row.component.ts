import { Component, ChangeDetectionStrategy, Input, Output, EventEmitter } from '@angular/core';
import { ComandoRowVM } from '../pac-comando.facade';
import { UrgencyBadgeComponent } from './urgency-badge.component';
import { SensitivitySelectorComponent } from './sensitivity-selector.component';
import { SyncStatusBadgeComponent, SyncStateDisplay } from './sync-status-badge.component';

// ---------------------------------------------------------------------------
// CommandRowComponent — PAC-FE-019 (P7 UX Polish)
// Presentational list row for the Command Catalog (SPEC-02 §1).
// Renders rótulo, gesto description, UrgencyBadge, SensitivitySelector
// (display mode), SyncStatusBadge, and action buttons.
//
// Outputs:
//   edit        — emitted when Edit button is clicked
//   recalibrate — emitted when Recalibrate button is clicked
//                 (only shown for 'published' commands)
//   retire      — emitted when Retire button is clicked
//
// Maps ComandoRowVM.syncState ('pending-sync' | 'active-on-device') to the
// simplified SyncStateDisplay ('pending' | 'synced') for the badge.
//
// NOTE: Uses @Input()/@Output() decorators (not input()/output() signals)
// because ts-jest JIT compilation does not support setInput() for signal
// inputs (same limitation as evnt/EventListComponent).
//
// Pure @Input/@Output — zero facade access.
// ---------------------------------------------------------------------------

@Component({
  selector: 'pac-command-row',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [UrgencyBadgeComponent, SensitivitySelectorComponent, SyncStatusBadgeComponent],
  template: `
    <div class="pac-command-row flex items-center justify-between gap-4 rounded-lg border border-line bg-surface p-4">
      <!-- Left: command info -->
      <div class="flex min-w-0 items-start gap-3">
        <div class="min-w-0">
          <div class="flex flex-wrap items-center gap-2">
            <span class="text-sm font-semibold text-ink">"{{ row.rotulo }}"</span>
            <pac-urgency-badge [urgencia]="row.urgencia" />
            <pac-sync-status-badge [syncState]="syncStateBadge" />
          </div>
          <div class="mt-0.5 flex items-center gap-2 text-xs text-ink-faint">
            <span>{{ gestoDescricao }}</span>
            <span>·</span>
            <span class="text-ink-soft">sensibilidade</span>
            <pac-sensitivity-selector [sensibilidade]="row.sensibilidade" [readonly]="true" />
          </div>
        </div>
      </div>

      <!-- Right: action buttons -->
      <div class="flex flex-shrink-0 items-center gap-1">
        <button
          type="button"
          class="pac-command-row__btn-edit grid size-8 place-items-center rounded-lg text-ink-soft transition-colors hover:bg-surface-2 hover:text-ink"
          title="Editar"
          (click)="onEdit()">
          <span class="material-symbols-outlined text-[18px]">edit</span>
        </button>

        @if (row.estado === 'published') {
          <button
            type="button"
            class="pac-command-row__btn-recalibrate grid size-8 place-items-center rounded-lg text-ink-soft transition-colors hover:bg-surface-2 hover:text-ink"
            title="Recalibrar (ajuste de sensibilidade sem rascunho)"
            (click)="onRecalibrate()">
            <span class="material-symbols-outlined text-[18px]">tune</span>
          </button>
        }

        <button
          type="button"
          class="pac-command-row__btn-retire grid size-8 place-items-center rounded-lg text-ink-soft transition-colors hover:bg-danger-bg hover:text-danger"
          title="Aposentar (desativar sem deletar)"
          (click)="onRetire()">
          <span class="material-symbols-outlined text-[18px]">archive</span>
        </button>
      </div>
    </div>
  `,
})
export class CommandRowComponent {
  /** The catalog row view-model (SPEC-03 / PAC-FE-013). */
  @Input({ required: true }) row!: ComandoRowVM;

  @Output() edit = new EventEmitter<ComandoRowVM>();
  @Output() recalibrate = new EventEmitter<ComandoRowVM>();
  @Output() retire = new EventEmitter<ComandoRowVM>();

  /** Maps SyncState domain type to simplified display type for the badge. */
  get syncStateBadge(): SyncStateDisplay {
    return this.row?.syncState === 'active-on-device' ? 'synced' : 'pending';
  }

  /** Human-readable gesto description (principal + optional secundário). */
  get gestoDescricao(): string {
    if (!this.row) return '';
    return this.row.primitivoSecundarioNome
      ? `${this.row.primitivoPrincipalNome} + ${this.row.primitivoSecundarioNome}`
      : this.row.primitivoPrincipalNome;
  }

  protected onEdit(): void {
    this.edit.emit(this.row);
  }

  protected onRecalibrate(): void {
    this.recalibrate.emit(this.row);
  }

  protected onRetire(): void {
    this.retire.emit(this.row);
  }
}
