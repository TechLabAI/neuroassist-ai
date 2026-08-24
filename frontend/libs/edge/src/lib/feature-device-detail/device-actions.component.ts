import {
  Component,
  ChangeDetectionStrategy,
  Input,
  output,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';

// ---------------------------------------------------------------------------
// DeviceActionsComponent — FE-EDGE-013
// Presentational: conductor-gated device management actions.
// Hidden (not disabled) when the session lacks `edge.device.manage` (DoD P3).
// The smart container (DeviceDetailPageComponent) owns confirmation dialogs.
//
// NOTE: @Input() is used instead of the input() signal API (ts-jest limitation).
//
// Standalone; OnPush; Signals-first; no facade dependency.
// ---------------------------------------------------------------------------

@Component({
  selector: 'edge-device-actions',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <!-- Hidden entirely when conductor entitlement is absent (DoD P3: hidden, not disabled) -->
    @if (_canManage()) {
      <!--
        Os <span> de ícone são LIGATURAS do Material Symbols: o texto do nó é o
        nome do ícone ("link_off"), não um glifo. Sem aria-hidden esse nome entra
        no nome acessível do botão, e o leitor de tela anuncia
        "link off Despareador" em vez de "Despareador" — e qualquer locator por
        nome exato passa a falhar. São decorativos: o rótulo textual ao lado já
        carrega o significado.
      -->
      <div class="flex flex-wrap gap-3" data-testid="device-actions-container">
        <!-- Re-pair: navigates to the pairing flow -->
        <a
          routerLink="/edge/pairing"
          class="inline-flex items-center gap-1.5 rounded-xl border border-brand bg-brand-subtle px-4 py-2 text-sm font-medium text-brand transition-colors hover:bg-brand hover:text-brand-ink">
          <span aria-hidden="true" class="material-symbols-outlined text-[18px]">link</span>
          Reparear
        </a>

        <!-- Unpair: emits to smart container which shows confirmation dialog -->
        <button
          (click)="unpairAction.emit()"
          class="inline-flex items-center gap-1.5 rounded-xl border border-line bg-surface px-4 py-2 text-sm font-medium text-ink-soft transition-colors hover:border-danger hover:text-danger-ink">
          <span aria-hidden="true" class="material-symbols-outlined text-[18px]">link_off</span>
          Despareador
        </button>

        <!-- Retire: emits to smart container which shows confirmation dialog -->
        <button
          (click)="retireAction.emit()"
          class="inline-flex items-center gap-1.5 rounded-xl border border-line bg-surface px-4 py-2 text-sm font-medium text-ink-soft transition-colors hover:border-danger hover:text-danger-ink">
          <span aria-hidden="true" class="material-symbols-outlined text-[18px]">cancel</span>
          Aposentar
        </button>
      </div>
    }
  `,
})
export class DeviceActionsComponent {
  /**
   * When false, the entire action block is hidden (not disabled).
   * Derived from DeviceDetailVM.canManage, which reflects the session's
   * `edge.device.manage` entitlement.
   */
  @Input({ required: true }) set canManage(value: boolean) {
    this._canManage.set(value);
  }

  /** Emitted when the conductor taps "Despareador" — host shows confirm dialog. */
  readonly unpairAction = output<void>();

  /** Emitted when the conductor taps "Aposentar" — host shows confirm dialog. */
  readonly retireAction = output<void>();

  protected readonly _canManage = signal(false);
}
