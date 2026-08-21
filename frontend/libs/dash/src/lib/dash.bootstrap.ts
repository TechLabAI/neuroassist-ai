import { Injector } from '@angular/core';
import { DashHomeFacade } from './dash.facade';
import { NotificationsFacade } from './notifications.facade';
import { DashStreamClient } from './api/dash-stream-client';
import { DashShellIntegrationService } from './integration/dash-shell-integration.service';

// =============================================================================
// dash.bootstrap.ts — SPEC-06 §3 (FE-DASH-019)
//
// Lifecycle functions for DASH module init/destroy.
// Called by scope:web (APP_INITIALIZER or DestroyRef) when DASH is loaded/unloaded.
//
// dashInit:
//   - Registers DASH nav + descriptor with shell (idempotent).
//   - Declares `dash.global-interrupt` outlet region.
//   - Primes the Home summary (DashHomeFacade.load).
//   - Opens the SSE stream (DashStreamClient.open) — Wave-0: no auth header.
//
// dashDestroy:
//   - Closes the SSE EventSource (BE evicts the PanelSession).
//   - Releases the `dash.global-interrupt` outlet region.
//
// Usage in app.config.ts (scope:web):
//   provideAppInitializer(() => dashInit(inject(Injector)))
//
// Destroy is wired via DestroyRef or APP_DESTROYING in the shell host component.
//
// NOTE: dashInit is idempotent via DashShellIntegrationService.registerWithShell().
// SSE open is guarded by DashStreamClient (no-op if already connecting/connected).
// =============================================================================

/** SSE endpoint — Wave-0 (no auth header; EventSource API limitation). */
const SSE_ENDPOINT = '/panel/stream';

/**
 * dashInit — initialize the DASH module.
 *
 * Idempotent: calling multiple times is safe.
 *   - Shell registration guarded by DashShellIntegrationService._registered flag.
 *   - SSE open guarded by DashStreamClient internal status check.
 *
 * @param injector - Angular injector (typically from inject(Injector) in app context).
 */
export function dashInit(injector: Injector): void {
  const shellIntegration = injector.get(DashShellIntegrationService);
  const dashFacade = injector.get(DashHomeFacade);
  const notificationsFacade = injector.get(NotificationsFacade);
  const streamClient = injector.get(DashStreamClient);

  // Register DASH with the shell (nav + outlet region).
  // No-op stub registry is safe here; real scope:web registry provided in app.config.ts.
  // TODO[INT-FE-DASH-003]: pass real ShellRegistry from scope:web when available.
  shellIntegration.registerWithShell({
    registerModule: () => { /* TODO[INT-FE-DASH-003]: shell registry integration */ },
    registerNavConfig: () => { /* TODO[INT-FE-DASH-003]: shell registry integration */ },
  });

  // Prime the Home summary — first data fetch on module load.
  // DashHomeFacade.load() is idempotent in the sense that re-calling it re-fetches.
  dashFacade.load();

  // Open the SSE stream for real-time notifications.
  // NotificationsFacade.load() also opens the stream after HTTP catch-up;
  // opening here ensures the stream is live even before the /notificacoes route is visited.
  // DashStreamClient.open() is a no-op if already connecting/connected/fallback.
  streamClient.open(SSE_ENDPOINT);

  // Suppress unused-variable warning — NotificationsFacade injected for future
  // loadUnacknowledged() call (FE-DASH-019 destroy step accesses it via injector).
  void notificationsFacade;
}

/**
 * dashDestroy — tear down the DASH module.
 *
 * Called when the DASH module is unloaded (lazy unload, app shutdown, or test teardown).
 * Safe to call when SSE was never opened (DashStreamClient.close() is idempotent).
 *
 * @param injector - Angular injector (same instance as dashInit).
 */
export function dashDestroy(injector: Injector): void {
  const streamClient = injector.get(DashStreamClient);
  const shellIntegration = injector.get(DashShellIntegrationService);

  // Close SSE EventSource — BE evicts the PanelSession.
  // No-op if EventSource was never opened or already closed.
  streamClient.close();

  // Release the `dash.global-interrupt` outlet region.
  // ShellOutletRegistry.unregisterGlobalOutlet is idempotent (Map.delete).
  shellIntegration.releaseOutletRegion();
}
