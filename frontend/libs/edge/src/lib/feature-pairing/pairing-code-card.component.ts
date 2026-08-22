import {
  Component,
  ChangeDetectionStrategy,
  Input,
  output,
  effect,
  signal,
} from '@angular/core';

// ---------------------------------------------------------------------------
// PairingCodeCardComponent — FE-EDGE-009
// Presentational: renders the enrollment token, TTL countdown, and copy button.
// Emits `expired` when the countdown transitions from > 0 to 0 (TTL boundary).
// Emits `copied` when the user taps the copy button.
//
// NOTE: @Input() is used instead of the input() signal API because ts-jest's
// JIT compilation of components with child imports does not support signal
// inputs for child components (same pattern as EVNT P2/P3, see RoutineNoticeComponent).
//
// Standalone; OnPush; Signals-first; no facade dependency — pure input/output.
// ---------------------------------------------------------------------------

@Component({
  selector: 'edge-pairing-code-card',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="rounded-xl border border-brand bg-brand-subtle px-5 py-4">
      <!-- Token display -->
      <div class="flex items-center justify-between gap-3">
        <div>
          <p class="text-xs font-medium text-ink-soft">Código de pareamento</p>
          <p class="mt-0.5 font-mono text-2xl font-bold tracking-widest text-brand">
            {{ _token() }}
          </p>
        </div>
        <!-- Copy button -->
        <button
          (click)="onCopy()"
          aria-label="Copiar código de pareamento"
          class="flex items-center gap-1.5 rounded-lg border border-brand px-3 py-1.5 text-xs font-medium text-brand transition-colors hover:bg-brand hover:text-brand-ink">
          <span class="material-symbols-outlined text-[16px]">content_copy</span>
          Copiar
        </button>
      </div>

      <!-- Countdown -->
      <div class="mt-3 flex items-center gap-1.5">
        <span class="material-symbols-outlined text-[16px]"
              [class.text-warning-ink]="_countdown() <= 30"
              [class.text-ink-faint]="_countdown() > 30">timer</span>
        <span class="text-xs"
              [class.text-warning-ink]="_countdown() <= 30"
              [class.text-ink-soft]="_countdown() > 30">
          Expira em {{ _countdown() }}s
        </span>
      </div>

      <!-- Instruction -->
      <p class="mt-2 text-xs text-ink-soft">
        Informe este código ao agente edge para concluir o registro.
      </p>
    </div>
  `,
})
export class PairingCodeCardComponent {
  /** One-time enrollment token from POST /devices (api-contract §2.1). */
  @Input({ required: true }) set token(value: string) {
    this._token.set(value);
  }

  /** Countdown in seconds derived from PairingFacade.countdown(). */
  @Input({ required: true }) set countdown(value: number) {
    this._countdown.set(value);
  }

  /** Emitted when the countdown transitions from > 0 to 0 (TTL boundary). */
  readonly expired = output<void>();

  /** Emitted when the user clicks the copy button. */
  readonly copied = output<void>();

  protected readonly _token = signal('');
  protected readonly _countdown = signal(0);

  constructor() {
    // Emit `expired` only when the countdown crosses the 0 boundary from above.
    // Guard `hadPositive` prevents a spurious emit when the component mounts
    // with countdown already at 0 (e.g., during tests or stale state).
    let hadPositive = false;
    effect(() => {
      const c = this._countdown();
      if (c > 0) {
        hadPositive = true;
      } else if (hadPositive) {
        this.expired.emit();
        hadPositive = false; // reset — only emit once per crossing
      }
    });
  }

  protected onCopy(): void {
    void navigator.clipboard.writeText(this._token()).catch(() => {
      // Clipboard API may be unavailable; no-op — no console.log per coding rules.
    });
    this.copied.emit();
  }
}
