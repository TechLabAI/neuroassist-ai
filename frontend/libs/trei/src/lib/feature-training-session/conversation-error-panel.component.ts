import {
  ChangeDetectionStrategy,
  Component,
  EventEmitter,
  Input,
  Output,
} from '@angular/core';
import { AppError } from '../trei.types';

// =============================================================================
// ConversationErrorPanelComponent — SPEC-02 §2 (FE-TREI-016)
//
// Presentational: error alert shown when status = 'error'.
// SPEC-07 / G5: never exposes correlationId in visible text — only the
// user-friendly `error.message` is rendered (correlationId is for debug logs only).
// =============================================================================

@Component({
  selector: 'trei-conversation-error-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      role="alert"
      class="rounded-2xl border border-error bg-error-bg p-4 text-sm text-error-ink"
      data-testid="session-error">
      <p class="font-semibold">Erro ao processar a sessão</p>
      @if (error?.message) {
        <!-- G5 guard: only error.message is displayed — correlationId is NEVER rendered -->
        <p class="mt-1 text-xs" data-testid="error-message">{{ error!.message }}</p>
      }
      <button
        (click)="retry.emit()"
        data-testid="retry-btn"
        class="mt-3 rounded-lg border border-error px-3 py-1.5 text-xs
               font-semibold text-error-ink hover:bg-error-bg">
        Tentar novamente
      </button>
    </div>
  `,
})
export class ConversationErrorPanelComponent {
  @Input() error: AppError | null = null;
  @Output() readonly retry = new EventEmitter<void>();
}
