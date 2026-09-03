import { ChangeDetectionStrategy, Component, EventEmitter, Output } from '@angular/core';

// =============================================================================
// EndSessionButtonComponent — SPEC-02 §2 (FE-TREI-015)
//
// Presentational: "Encerrar sessão" trigger.
// No-loss guarantee is enforced by TrainingSessionFacade.endSession() — proposals
// are never discarded on session end (INV-4, SPEC-05).
// =============================================================================

@Component({
  selector: 'trei-end-session-button',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button
      (click)="end.emit()"
      data-testid="end-session-btn"
      class="rounded-xl border border-line px-4 py-2 text-sm text-ink-soft hover:bg-surface-2">
      Encerrar sessão
    </button>
  `,
})
export class EndSessionButtonComponent {
  @Output() readonly end = new EventEmitter<void>();
}
