import {
  Component,
  ChangeDetectionStrategy,
  Input,
} from '@angular/core';
import { toDisplayTime } from '../../evnt.types';

// =============================================================================
// ConfirmationTrailComponent — SPEC-02 §2, FE-EVNT-017
// Presentational: shows who confirmed an event and when.
// Renders nothing when confirmadoPor is null (no confirmation yet).
// Uses toDisplayTime (from evnt.types) to format the confirmadoAt timestamp.
// Zero DI — all state via @Input().
//
// NOTE: @Input() used instead of input() signal API for ts-jest consistency (P3 convention).
// =============================================================================

@Component({
  selector: 'evnt-confirmation-trail',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (confirmadoPor) {
      <div
        class="mt-4 flex items-center gap-2 rounded-lg border border-surface-border bg-surface-subtle px-4 py-3 text-sm text-ink-soft"
        data-testid="confirmation-trail"
        role="status"
        aria-live="polite">
        <span class="material-symbols-outlined text-[16px] text-success" aria-hidden="true">check_circle</span>
        <span>
          Confirmado por <strong>{{ confirmadoPor }}</strong>
          {{ confirmedAt() }}
        </span>
      </div>
    }
  `,
})
export class ConfirmationTrailComponent {
  /** Name of the caregiver who confirmed the event. Null if not yet confirmed. */
  @Input() confirmadoPor: string | null = null;

  /** ISO-8601 UTC timestamp of confirmation. Null if not yet confirmed. */
  @Input() confirmadoAt: string | null = null;

  /** Formats the confirmation timestamp as "às HHhMM" (pt-BR). Returns '' if null. */
  protected confirmedAt(): string {
    return this.confirmadoAt ? toDisplayTime(this.confirmadoAt) : '';
  }
}
