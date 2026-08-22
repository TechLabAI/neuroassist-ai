import {
  Component,
  ChangeDetectionStrategy,
  Input,
  computed,
  signal,
} from '@angular/core';
import { DerivedState } from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// ConnectivityStatusCardComponent — FE-EDGE-012
// Presentational: renders a distinct badge for each of the 5 DerivedState values.
// Color + text + icon must differ for every state (DoD P3, DoD P7).
//
// NOTE: @Input() is used instead of the input() signal API (ts-jest limitation).
//
// Standalone; OnPush; Signals-first; no facade dependency.
// Accessible: role="status" + aria-label carry the state description to SR.
// ---------------------------------------------------------------------------

interface StatePresentation {
  badgeClass: string;
  icon: string;
  label: string;
  description: string;
}

function statePresentation(state: DerivedState): StatePresentation {
  switch (state) {
    case 'online':
      return {
        badgeClass: 'bg-success-bg text-success-ink border-success',
        icon: 'wifi',
        label: 'Online',
        description: 'Dispositivo online — heartbeat recente recebido.',
      };
    case 'stale':
      return {
        badgeClass: 'bg-warning-bg text-warning-ink border-warning',
        icon: 'wifi_find',
        label: 'Sinal fraco',
        description: 'Sinal fraco — último heartbeat mais antigo que o esperado.',
      };
    case 'offline':
      return {
        badgeClass: 'bg-danger-bg text-danger-ink border-danger',
        icon: 'wifi_off',
        label: 'Offline',
        description: 'Dispositivo offline — nenhum heartbeat dentro da janela de tempo.',
      };
    case 'unknown':
      return {
        badgeClass: 'bg-surface-2 text-ink-soft border-line',
        icon: 'device_unknown',
        label: 'Desconhecido',
        description: 'Conectividade desconhecida — falha na última verificação.',
      };
    case 'not-paired':
      return {
        badgeClass: 'bg-surface-2 text-ink-faint border-line',
        icon: 'link_off',
        label: 'Não pareado',
        description: 'Nenhum dispositivo pareado a este paciente.',
      };
  }
}

@Component({
  selector: 'edge-connectivity-status-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      role="status"
      [attr.aria-label]="presentation().description"
      class="flex items-start gap-3 rounded-xl border p-4"
      [class]="presentation().badgeClass">

      <!-- Icon -->
      <span class="material-symbols-outlined mt-0.5 text-[22px]">
        {{ presentation().icon }}
      </span>

      <!-- Label + metadata -->
      <div>
        <p class="font-semibold leading-snug">{{ presentation().label }}</p>

        @if (_lastSyncRelative()) {
          <p class="mt-0.5 text-xs opacity-80">
            Última sincronização: {{ _lastSyncRelative() }}
          </p>
        }

        @if (_versao()) {
          <p class="mt-0.5 text-xs opacity-80">Versão: {{ _versao() }}</p>
        }
      </div>
    </div>
  `,
})
export class ConnectivityStatusCardComponent {
  /** FE-derived connectivity state (never from a raw BE status flag). */
  @Input({ required: true }) set state(value: DerivedState) {
    this._state.set(value);
  }

  /** Human-readable relative sync time, e.g. "há 2 min". */
  @Input() set lastSyncRelative(value: string) {
    this._lastSyncRelative.set(value);
  }

  /** Agent version string, or null when not yet reported. */
  @Input() set versao(value: string | null) {
    this._versao.set(value);
  }

  protected readonly _state           = signal<DerivedState>('not-paired');
  protected readonly _lastSyncRelative = signal<string>('—');
  protected readonly _versao           = signal<string | null>(null);

  protected readonly presentation = computed(() => statePresentation(this._state()));
}

// Export the pure helper for unit testing (DoD P3: all 5 states verified)
export { statePresentation };
