import { Component, ChangeDetectionStrategy, Input } from '@angular/core';

// =============================================================================
// EvntDegradedModeBannerComponent — FE-EVNT-024 / P7
//
// Visible non-blocking banner when the SSE channel has degraded to polling.
// Informs the user the system is still checking, just less frequently.
//
// Input: pollIntervalSec — interval in seconds (pollIntervalMs / 1000 from facade).
// Template: <aside role="status"> so it's announced by screen readers without
// interrupting the current flow (role="alert" is too intrusive for a persistent
// status message — see SPEC-07 §3).
//
// Zero DI — purely presentational (plan P7).
// a11y: role="status" + aria-live="polite" ensure AT users are notified once
// without preempting active reading.
//
// NOTE: @Input() is used instead of the input() signal API because ts-jest's
// JIT compilation of components with child imports does not support setInput()
// for signal inputs (same limitation as RoutineNoticeComponent — P2/P7).
// =============================================================================

@Component({
  selector: 'evnt-degraded-mode-banner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <aside
      class="evnt-degraded-banner"
      role="status"
      aria-live="polite"
      data-testid="degraded-banner">
      <span class="evnt-degraded-icon" aria-hidden="true">&#9888;</span>
      <span>Modo degradado — verificando a cada {{ pollIntervalSec }}s</span>
    </aside>
  `,
  styles: [
    `
      .evnt-degraded-banner {
        display: flex;
        align-items: center;
        gap: 0.5rem;
        padding: 0.75rem 1rem;
        margin-bottom: 1rem;
        border-radius: 0.5rem;
        font-size: 0.875rem;
        background-color: rgba(161, 100, 0, 0.1);
        color: #92600a;
      }

      .evnt-degraded-icon {
        font-size: 1rem;
        flex-shrink: 0;
      }
    `,
  ],
})
export class EvntDegradedModeBannerComponent {
  /** Polling interval in seconds, derived from feature flag (pollIntervalMs / 1000). */
  @Input() pollIntervalSec = 15;
}
