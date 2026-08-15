import {
  Component,
  ChangeDetectionStrategy,
  Input,
  signal,
  computed,
} from '@angular/core';

// ---------------------------------------------------------------------------
// RelativeTimeComponent — FE-EDGE-017 (P7 UX Polish)
//
// Pure presentational component that formats an ISO-8601 timestamp as a
// pt-BR relative-time string ("há 2 min", "há 1 hora", etc.).
//
// Uses a `<time>` element so the raw ISO value is machine-readable while
// the human-friendly label is visible (a11y, SEO).
//
// NOTE: @Input() decorator is used instead of the input() signal API
// because ts-jest's JIT compilation does not support signal inputs
// (same established pattern as PairingCodeCardComponent and EVNT components).
//
// Standalone + OnPush. No injected services; purely derived from input.
// ---------------------------------------------------------------------------

/**
 * Format an ISO-8601 timestamp to a pt-BR relative-time string.
 * Exported as a pure function for independent unit testing.
 */
export function toRelativePtBR(isoStr: string | null | undefined): string {
  if (!isoStr) return '—';
  const diffMs = Date.now() - new Date(isoStr).getTime();
  if (diffMs < 0) return 'agora';
  const diffSec = Math.floor(diffMs / 1000);
  if (diffSec < 60) return 'há menos de 1 min';
  const diffMin = Math.floor(diffSec / 60);
  if (diffMin === 1) return 'há 1 min';
  if (diffMin < 60) return `há ${diffMin} min`;
  const diffHr = Math.floor(diffMin / 60);
  if (diffHr === 1) return 'há 1 hora';
  if (diffHr < 24) return `há ${diffHr} horas`;
  const diffDay = Math.floor(diffHr / 24);
  if (diffDay === 1) return 'há 1 dia';
  return `há ${diffDay} dias`;
}

@Component({
  selector: 'edge-relative-time',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <time
      [attr.datetime]="_isoDate() ?? null"
      [attr.title]="_isoDate() ?? null"
      class="tabular-nums">{{ label() }}</time>
  `,
})
export class RelativeTimeComponent {
  protected readonly _isoDate = signal<string | null | undefined>(null);

  /** ISO-8601 UTC timestamp to format as pt-BR relative time. */
  @Input() set isoDate(value: string | null | undefined) {
    this._isoDate.set(value);
  }

  readonly label = computed(() => toRelativePtBR(this._isoDate()));
}
