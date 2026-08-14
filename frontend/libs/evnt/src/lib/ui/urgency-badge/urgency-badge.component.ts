import {
  Component,
  ChangeDetectionStrategy,
  input,
  computed,
} from '@angular/core';

// =============================================================================
// UrgencyBadgeComponent — SPEC-02 §3, FE-EVNT-018
// Presentational badge that signals urgency level via color.
// Emergency → badge-alarm (--color-alarm CSS custom property; fallback to red)
// Routine   → badge-routine (--color-routine CSS custom property; fallback to grey)
// Zero DI — receives all data through input().
// =============================================================================

@Component({
  selector: 'evnt-urgency-badge',
  standalone: true,
  template: `
    <span [class]="badgeClass()" [attr.aria-label]="'Urgência: ' + urgencia()">
      {{ urgencia() === 'emergencia' ? 'Emergência' : 'Rotina' }}
    </span>
  `,
  styles: [
    `
      :host {
        display: inline-flex;
      }

      .badge-alarm {
        display: inline-flex;
        align-items: center;
        border-radius: 9999px;
        padding: 0.125rem 0.625rem;
        font-size: 0.75rem;
        font-weight: 600;
        background-color: var(--color-alarm, #fee2e2);
        color: var(--color-alarm-ink, #991b1b);
      }

      .badge-routine {
        display: inline-flex;
        align-items: center;
        border-radius: 9999px;
        padding: 0.125rem 0.625rem;
        font-size: 0.75rem;
        font-weight: 600;
        background-color: var(--color-routine, #f3f4f6);
        color: var(--color-routine-ink, #374151);
      }
    `,
  ],
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UrgencyBadgeComponent {
  /** Urgency level — drives badge color class. (Optional input with default for testing) */
  readonly urgencia = input<'emergencia' | 'rotina'>('rotina');

  /** Computed CSS class: 'badge-alarm' for emergency, 'badge-routine' for routine. */
  protected readonly badgeClass = computed(() =>
    this.urgencia() === 'emergencia' ? 'badge-alarm' : 'badge-routine'
  );
}
