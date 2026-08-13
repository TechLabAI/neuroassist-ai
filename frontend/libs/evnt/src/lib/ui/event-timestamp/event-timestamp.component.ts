import {
  Component,
  ChangeDetectionStrategy,
  input,
  computed,
} from '@angular/core';

// =============================================================================
// EventTimestampComponent — SPEC-02 §3, FE-EVNT-018
// Presentational formatter for ISO-8601 timestamps.
// Renders "às HH:mm" in pt-BR locale using Intl.DateTimeFormat.
// Zero DI — pure input/output, no services.
// =============================================================================

/**
 * Formats an ISO-8601 UTC string into "às HH:mm" (pt-BR locale).
 * Uses Intl.DateTimeFormat with timeStyle:'short' — output is locale-aware
 * and may render as "14:32" or "14h32" depending on runtime ICU data.
 */
export function formatTimestamp(iso: string): string {
  return (
    'às ' +
    new Intl.DateTimeFormat('pt-BR', { timeStyle: 'short' }).format(
      new Date(iso)
    )
  );
}

@Component({
  selector: 'evnt-event-timestamp',
  standalone: true,
  template: `
    <time [attr.datetime]="recognizedAt()" class="text-xs text-ink-soft">
      {{ formatted() }}
    </time>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EventTimestampComponent {
  /** ISO-8601 UTC string from the EventoVm (recognizedAt field). (Optional input with default for testing) */
  readonly recognizedAt = input<string>(new Date().toISOString());

  /** Formatted "às HH:mm" string derived from recognizedAt signal. */
  protected readonly formatted = computed(() =>
    formatTimestamp(this.recognizedAt())
  );
}
