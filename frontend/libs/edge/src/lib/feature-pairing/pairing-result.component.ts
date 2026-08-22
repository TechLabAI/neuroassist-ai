import {
  Component,
  ChangeDetectionStrategy,
  Input,
  output,
  computed,
  signal,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { PairingPhase } from '@neuroassist/shared/contracts';

// ---------------------------------------------------------------------------
// PairingResultComponent — FE-EDGE-010
// Presentational: outcome display after the pairing wizard completes.
// Handles success (paired), expiry, and error outcomes.
//
// NOTE: @Input() is used instead of the input() signal API (ts-jest limitation).
//
// Standalone; OnPush; Signals-first; no facade dependency.
// ---------------------------------------------------------------------------

@Component({
  selector: 'edge-pairing-result',
  standalone: true,
  imports: [RouterLink],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (isSuccess()) {
      <div class="flex flex-col items-center gap-4 py-4 text-center">
        <span class="material-symbols-outlined text-[48px] text-success">check_circle</span>
        <div>
          <p class="font-display text-base font-bold text-ink">Dispositivo pareado!</p>
          @if (_versao()) {
            <p class="mt-1 text-xs text-success-ink">Online · versão {{ _versao() }}</p>
          }
        </div>
        <a routerLink="/edge/device"
           class="mt-2 inline-flex items-center gap-2 rounded-xl bg-success px-5 py-2 text-sm font-bold text-success-ink">
          <span class="material-symbols-outlined text-[16px]">arrow_forward</span>
          Ver dispositivo
        </a>
      </div>
    }

    @if (isExpired()) {
      <div class="flex flex-col items-center gap-4 py-4 text-center">
        <span class="material-symbols-outlined text-[40px] text-warning">timer_off</span>
        <div>
          <p class="text-sm font-medium text-ink">Código expirado</p>
          @if (_errorMessage()) {
            <p class="mt-1 text-xs text-ink-soft">{{ _errorMessage() }}</p>
          }
        </div>
        <button (click)="retry.emit()"
                class="rounded-xl bg-brand px-5 py-2.5 text-sm font-bold text-brand-ink hover:bg-brand-strong">
          Novo código
        </button>
      </div>
    }

    @if (isError()) {
      <div class="flex flex-col items-center gap-4 py-4 text-center">
        <span class="material-symbols-outlined text-[40px] text-danger">error</span>
        <div>
          <p class="text-sm font-medium text-ink">Erro no pareamento</p>
          @if (_errorMessage()) {
            <p class="mt-1 text-xs text-ink-soft">{{ _errorMessage() }}</p>
          }
        </div>
        <button (click)="retry.emit()"
                class="rounded-xl bg-brand px-5 py-2.5 text-sm font-bold text-brand-ink hover:bg-brand-strong">
          Tentar novamente
        </button>
      </div>
    }
  `,
})
export class PairingResultComponent {
  @Input({ required: true }) set phase(value: PairingPhase) {
    this._phase.set(value);
  }

  @Input() set versao(value: string | null) {
    this._versao.set(value);
  }

  @Input() set errorMessage(value: string | null) {
    this._errorMessage.set(value);
  }

  /** Emitted when the user clicks "Novo código" or "Tentar novamente". */
  readonly retry = output<void>();

  protected readonly _phase        = signal<PairingPhase>('idle');
  protected readonly _versao       = signal<string | null>(null);
  protected readonly _errorMessage = signal<string | null>(null);

  protected readonly isSuccess = computed(() => this._phase() === 'paired');
  protected readonly isExpired = computed(() => this._phase() === 'expired');
  protected readonly isError   = computed(() => this._phase() === 'error');
}
