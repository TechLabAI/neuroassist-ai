import { inject } from '@angular/core';
import { EdgeShellIntegrationService } from './integration/edge-shell-integration.service';
import { DeviceFacade } from './edge.facade';
import { EDGE_ERROR_MESSAGES } from './edge-error-messages';

// ---------------------------------------------------------------------------
// edgeBootstrap — SPEC-06 §4
// init:    resolve the active patient's device and START connectivity polling
//          so the topbar chip is honest from first paint.
//          Register descriptor + composition slots with the shell.
//          Does NOT open any pairing flow.
// destroy: stop the connectivity poll timer (no leaked interval on unload).
// ---------------------------------------------------------------------------

export const edgeBootstrap = {
  init(): void {
    // TODO[FE-EDGE-001]: inject EmpresaContextService to get the real active pacienteId
    // For now, the integration service is registered; polling starts when the
    // shell calls EdgeShellIntegrationService.startConnectivityForActivePatient(pacienteId).
    try {
      const integration = inject(EdgeShellIntegrationService);
      integration.registerWithShell();

      // INT-FE-EDGE-006: EDGE error messages are available at module init.
      // TODO[INT-FE-EDGE-006]: register with SharedErrorModelService.register('edge', EDGE_ERROR_MESSAGES)
      // when SharedErrorModelService implements the registration interface.
      void EDGE_ERROR_MESSAGES; // tree-shake guard: ensures the map is included in the bundle

      // Resolve connectivity for the active patient (placeholder pacienteId)
      // TODO[FE-EDGE-001]: get pacienteId from EmpresaContextService
      integration.startConnectivityForActivePatient('pac-marcio');
    } catch {
      // inject() may not be available outside injection context during lazy load.
      // registerWithShell() is idempotent — will be called again when context is ready.
    }
  },

  destroy(): void {
    // Last-line guarantee: stop the poll timer when the module unloads.
    // Reinforces DeviceFacade.stopPolling() called by DeviceDetailPageComponent.ngOnDestroy.
    try {
      const facade = inject(DeviceFacade);
      facade.stopPolling();
    } catch {
      // Guard against injection context unavailability during lazy unload.
    }
  },
};
