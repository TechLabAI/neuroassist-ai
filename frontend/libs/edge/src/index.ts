// =============================================================================
// @neuroassist/edge — public API barrel
// Imported by scope:web shell pages (via EdgeShellIntegrationService) and the
// thin /edge dev host. Other scope:* modules MUST NOT import from here directly
// — they use EdgeShellIntegrationService.connectivityReadModel() or
// EdgeShellIntegrationService.resolvePairingSlot() to preserve the nx-boundary.
// =============================================================================

// Shell descriptor and routes (consumed by scope:web app.routes.ts)
export { EDGE_MODULE_DESCRIPTOR } from './lib/edge.descriptor';
export { EDGE_ROUTES } from './lib/edge.routes';
export { edgeBootstrap } from './lib/edge.bootstrap';

// Composition components (scope:web only — not for scope:pac/scope:acs)
export { ConnectivityChipComponent } from './lib/feature-connectivity/connectivity-chip.component';
export { DevicePairingPageComponent } from './lib/feature-pairing/device-pairing-page.component';
export { DeviceDetailPageComponent } from './lib/feature-device-detail/device-detail-page.component';

// Integration service (scope:web injects this; other modules use its composition methods)
export { EdgeShellIntegrationService } from './lib/integration/edge-shell-integration.service';

// Facades (scope:web and thin host; other modules go via integration service)
export { DeviceFacade } from './lib/edge.facade';
export { PairingFacade } from './lib/edge-pairing.facade';

// Providers factory
export { provideEdge } from './lib/edge.providers';
export type { EdgeProviderConfig } from './lib/edge.providers';

// Port tokens (needed by app.config.ts to override adapters)
export { DEVICE_PORT } from './lib/api/device.port';
export type { DevicePort } from './lib/api/device.port';
export { PAIRING_PORT } from './lib/api/pairing.port';
export type { PairingPort } from './lib/api/pairing.port';
