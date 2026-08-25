import { inject } from '@angular/core';
import { GestureTestFacade } from './gest.facade';
import { GestShellIntegrationService } from './integration/gest-shell-integration.service';
import { DetectionContractService } from './api/detection-contract.service';

// ---------------------------------------------------------------------------
// gestBootstrap — SPEC-06 §4
// init: negotiate detection-contract version + register with shell.
//       Does NOT open camera or stream (privacy-by-default, ADR-005).
// destroy: ensure no MediaStream leaks when module unloads.
//
// negotiate() is fire-and-forget (void init signature preserved for callers).
// Fail-safe: negotiate() never throws — network errors return a safe fallback.
//
// What is explicitly deferred (GEST-FE-009 remaining scope):
//   Per-stream compatibility validation — checking that ENGINE_FEATURE matches
//   contract.requiredFields before openStream(). This requires coordinating with
//   EdgeDetectionBrowserAdapter.init() (ADR-007) and is tracked separately.
// ---------------------------------------------------------------------------

export const gestBootstrap = {
  init(): void {
    // Step 1: negotiate the detection contract and cache the descriptor.
    // Fire-and-forget — negotiate() is fail-safe and never throws.
    try {
      const contractService = inject(DetectionContractService);
      void contractService.negotiate();
    } catch {
      // inject() may not be available outside injection context during lazy load.
      // negotiate() will be called on the first stream open instead.
    }

    // Step 2: register composition slot with shell integration service
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
