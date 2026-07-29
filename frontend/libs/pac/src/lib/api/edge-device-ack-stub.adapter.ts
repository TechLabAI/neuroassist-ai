import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { SyncState } from './comando.port';

// ---------------------------------------------------------------------------
// EdgeDeviceAckStubAdapter — ADR-005 (command-edit-not-live-until-synced)
//
// PAC-FE MUST NEVER claim a command is live on the device without explicit
// EDGE device acknowledgment. Since the EDGE device-ack contract is absent
// in MVP (gap tracked in INT-FE-PAC-001), this stub adapter ALWAYS returns
// 'pending-sync'. The honest-state banner ("pendente de sincronização") is
// displayed for every command in the catalog.
//
// TODO[INT-FE-PAC-001]: Replace with real EdgeDeviceAckAdapter when the EDGE
// heartbeat contract defines the ack signal. Until then, this stub enforces
// the ADR-005 invariant: no command is shown as 'active-on-device' without
// evidence.
// ---------------------------------------------------------------------------

@Injectable()
export class EdgeDeviceAckStubAdapter {
  /**
   * Always returns 'pending-sync' for any command ID.
   * Replacement adapter must emit 'active-on-device' only after confirmed
   * EDGE heartbeat carries the comandoId in its acked-catalog payload.
   */
  getSyncState(_comandoId: string): Observable<SyncState> {
    return of('pending-sync' as SyncState);
  }
}
