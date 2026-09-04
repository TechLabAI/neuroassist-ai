import { ChangeDetectionStrategy, Component } from '@angular/core';

// =============================================================================
// SessionEndedNoticeComponent — SPEC-02 §2 (FE-TREI-016)
//
// Presentational: success notice shown after the session is ended.
// Communicates INV-4: pending proposals are NOT discarded.
// No inputs or outputs — shown conditionally by the smart parent.
// =============================================================================

@Component({
  selector: 'trei-session-ended-notice',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div
      class="rounded-xl border border-success bg-success-bg p-3 text-sm text-success-ink"
      data-testid="session-ended-notice"
      role="status"
      aria-live="polite">
      Sessão encerrada. As propostas pendentes continuam como rascunhos.
    </div>
  `,
})
export class SessionEndedNoticeComponent {}
