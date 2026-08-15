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

// Presentational sub-components — pairing flow (FE-EDGE-009, FE-EDGE-010)
export { PairingCodeCardComponent } from './lib/feature-pairing/pairing-code-card.component';
export { PairingProgressComponent } from './lib/feature-pairing/pairing-progress.component';
export { PairingResultComponent } from './lib/feature-pairing/pairing-result.component';

// Presentational sub-components — device detail (FE-EDGE-012, FE-EDGE-013)
export { ConnectivityStatusCardComponent } from './lib/feature-device-detail/connectivity-status-card.component';
export { DeviceMetaListComponent } from './lib/feature-device-detail/device-meta-list.component';
export { DeviceActionsComponent } from './lib/feature-device-detail/device-actions.component';

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

// Error messages — SPEC-07 §1 pt-BR copy table (INT-FE-EDGE-006)
export { EDGE_ERROR_MESSAGES, resolveEdgeError } from './lib/edge-error-messages';
export type { EdgeErrorConfig, EdgeErrorKind, EdgeErrorSurface } from './lib/edge-error-messages';

// Navigation — breadcrumb state (FE-EDGE-016)
// EdgeBreadcrumbService is providedIn:'root'; import it in the app config or bootstrap to activate.
// EDGE_BREADCRUMB_PORT is the InjectionToken; provide the real BreadcrumbService via:
//   { provide: EDGE_BREADCRUMB_PORT, useExisting: BreadcrumbService }
export { EdgeBreadcrumbService } from './lib/navigation/edge-breadcrumb.service';
export { EDGE_BREADCRUMB_PORT } from './lib/navigation/breadcrumb.port';
export type { BreadcrumbItem, BreadcrumbPort } from './lib/navigation/breadcrumb.port';

// UI components — shared presentational (FE-EDGE-017 P7)
export { RelativeTimeComponent } from './lib/ui/relative-time.component';
export { toRelativePtBR } from './lib/ui/relative-time.component';

// Toast token — G6 retry-backoff notification contract (FE-EDGE-019 P7)
// Host app provides the concrete implementation via:
//   { provide: EDGE_TOAST_TOKEN, useExisting: ToastService }
// Or shared/ui-feedback once available.
export { EDGE_TOAST_TOKEN, EDGE_RETRY_TOAST_MESSAGE } from './lib/edge-toast.token';
export type { EdgeToastService } from './lib/edge-toast.token';
