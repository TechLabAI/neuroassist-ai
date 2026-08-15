import { Component, ChangeDetectionStrategy, Input } from '@angular/core';

// ---------------------------------------------------------------------------
// SyncStatusBadgeComponent — PAC-FE-019 (P7 UX Polish)
// Honest-state badge per ADR-005. In MVP, EdgeDeviceAckStubAdapter ensures
// syncState is always 'pending-sync' (mapped to 'pending' by the parent row).
//
// - 'pending' → "pendente de sincronização" (warning tone)
// - 'synced'  → "ativo no dispositivo"      (success tone)
//
// NOTE: Uses @Input() decorator (not input() signal) because ts-jest JIT
// compilation does not support setInput() for signal inputs (known limitation).
//
// Pure @Input — zero facade access.
// ---------------------------------------------------------------------------

export type SyncStateDisplay = 'synced' | 'pending';

@Component({
  selector: 'pac-sync-status-badge',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (syncState === 'pending') {
      <span class="sync-status-badge sync-status-badge--pending inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium bg-warning-bg text-warning-ink">
        pendente de sincronização
      </span>
    } @else {
      <span class="sync-status-badge sync-status-badge--synced inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-xs font-medium bg-success-bg text-success-ink">
        ativo no dispositivo
      </span>
    }
  `,
})
export class SyncStatusBadgeComponent {
  /** Defaults to 'pending' — the honest state for MVP (EDGE ack not yet contracted). */
  @Input() syncState: SyncStateDisplay = 'pending';
}
