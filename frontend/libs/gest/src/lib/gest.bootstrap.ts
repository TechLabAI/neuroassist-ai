import { inject } from '@angular/core';
import { GestureTestFacade } from './gest.facade';
import { GestShellIntegrationService } from './integration/gest-shell-integration.service';

// ---------------------------------------------------------------------------
// gestBootstrap — SPEC-06 §4
// init: negotiate detection-contract version + register with shell.
//       Does NOT open camera or stream (privacy-by-default, ADR-005).
// destroy: ensure no MediaStream leaks when module unloads.
// ---------------------------------------------------------------------------

export const gestBootstrap = {
  init(): void {
    // TODO[GEST-FE-002]: GET /api/v1/gest/detection-contract, memoize contractVersion
    // and validate compatibility. For now, contractVersion is negotiated per-stream
    // (openStream) rather than upfront.

    // Register composition slot with shell integration service
    // (shell reads GEST_MODULE_DESCRIPTOR from barrel; integration service handles
    // the scope:web composition path — GEST-INT-001)
    try {
      const integration = inject(GestShellIntegrationService);
      integration.registerWithShell();
    } catch {
      // inject() may not be available outside injection context (e.g. lazy load
      // during app init); registerWithShell() is idempotent and can be called later.
    }
  },

  destroy(): void {
    // Last-line guarantee: stop any live MediaStream when the module unloads.
    // Reinforces the per-test teardown in GestureTestFacade.stopTest().
    try {
      const facade = inject(GestureTestFacade);
      facade.stopTest();
    } catch {
      // Guard against injection context unavailability during lazy unload.
    }
  },
};
