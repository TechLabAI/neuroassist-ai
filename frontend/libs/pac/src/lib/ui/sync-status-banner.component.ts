import { Component, ChangeDetectionStrategy, Input } from '@angular/core';

// ---------------------------------------------------------------------------
// SyncStatusBannerComponent — PAC-FE-020 (P7 UX Polish)
// ADR-005 honest-state surface: shown in CommandEditorComponent whenever
// editorVM.syncState === 'pending-sync' (mapped to 'pending' by the host).
//
// When 'pending': displays "pendente de sincronização" informational banner
//   (SPEC-07: not an error — informative, non-blocking).
// When 'synced': renders nothing (or hidden state).
//
// In MVP the stub EdgeDeviceAckStubAdapter always returns 'pending-sync',
// so this banner is always visible until the EDGE device-ack contract is
// implemented (INT-FE-PAC-001).
//
// NOTE: Uses @Input() decorator (not input() signal) because ts-jest JIT
// compilation does not support setInput() for signal inputs (known limitation).
//
// Pure @Input — zero facade access.
// ---------------------------------------------------------------------------

export type SyncStateBanner = 'synced' | 'pending';

@Component({
  selector: 'pac-sync-status-banner',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (syncState === 'pending') {
      <div class="sync-status-banner rounded-md border border-warning-ink/20 bg-warning-bg px-4 py-3 text-sm text-warning-ink">
        <div class="flex items-start gap-2">
          <span class="material-symbols-outlined mt-0.5 flex-shrink-0 text-[16px]">sync_problem</span>
          <p>
            Este comando está <strong>pendente de sincronização</strong> com o dispositivo edge.
            As alterações serão aplicadas quando o dispositivo se reconectar.
          </p>
        </div>
      </div>
    }
  `,
})
export class SyncStatusBannerComponent {
  /** 'pending' → show banner; 'synced' → banner hidden (no DOM element).
   * Defaults to 'pending' — honest state for MVP (EDGE ack not contracted). */
  @Input() syncState: SyncStateBanner = 'pending';
}
