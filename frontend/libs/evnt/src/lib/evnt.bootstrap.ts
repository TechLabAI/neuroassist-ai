import { inject } from '@angular/core';
import { EventDeliveryFacade } from './evnt.facade';
import { EvntShellIntegrationService } from './integration/evnt-shell-integration.service';

// =============================================================================
// evntBootstrap — SPEC-06 §3
// init order is load-bearing (must match SPEC-06 §3):
//   1. registerAlarmOutlet() — shell outlet ready BEFORE any event arrives
//   2. hydrate()             — GET /events/active; refresh = overlay before first SSE
//   3. startLiveChannel()    — open SSE (or start poll if degraded/flag off)
// destroy:
//   1. stopLiveChannel()     — close SSE / polling interval (no leak)
//   2. unregisterAlarmOutlet()
// =============================================================================

export const evntBootstrap = {
  async init(): Promise<void> {
    try {
      const integration = inject(EvntShellIntegrationService);
      const facade = inject(EventDeliveryFacade);

      // (1) Register the alarm outlet first — overlay must be ready before events arrive
      integration.registerAlarmOutlet();

      // (2) Hydrate: GET /events/active ensures overlay re-renders on page refresh
      //     even before the first SSE frame arrives
      await facade.hydrate();

      // (3) Open live channel (SSE primary; polling fallback on error — SPEC-04 §3/§4)
      facade.startLiveChannel();
    } catch {
      // Bootstrap errors are non-fatal; the polling fallback handles recovery
    }
  },

  destroy(): void {
    try {
      const facade = inject(EventDeliveryFacade);
      const integration = inject(EvntShellIntegrationService);

      // Teardown order: stop stream first, then unregister outlet
      facade.stopLiveChannel();
      integration.unregisterAlarmOutlet();
    } catch {
      // Guard against injection context unavailability during lazy unload
    }
  },
};
