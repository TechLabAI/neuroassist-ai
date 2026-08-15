import { Component, ChangeDetectionStrategy, Input } from '@angular/core';

// ---------------------------------------------------------------------------
// UrgencyBadgeComponent — PAC-FE-019 (P7 UX Polish)
// Presentational urgency badge for the Command Catalog list row.
// 'emergencia' → danger tone; 'rotina' → info tone.
//
// NOTE: Uses @Input() decorator (not input() signal) because ts-jest JIT
// compilation does not support setInput() for signal inputs (known limitation
// also present in evnt/EventListComponent). See EVNT EventListComponent note.
//
// Pure @Input — zero facade access.
// ---------------------------------------------------------------------------

export type UrgenciaType = 'emergencia' | 'rotina';

@Component({
  selector: 'pac-urgency-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (urgencia === 'emergencia') {
      <span class="urgency--emergencia inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium bg-danger-bg text-danger">Emergência</span>
    } @else {
      <span class="urgency--rotina inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium bg-info-bg text-info">Rotina</span>
    }
  `,
})
export class UrgencyBadgeComponent {
  /** Urgency type — drives badge color. Defaults to 'rotina'. */
  @Input() urgencia: UrgenciaType = 'rotina';
}
