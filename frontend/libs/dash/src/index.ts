// =============================================================================
// @neuroassist/dash — public API barrel
// Imported by scope:web shell (app.routes.ts, app.config.ts) and shell host pages.
// Modules must NEVER import each other's feature libs directly (nx-boundaries).
// =============================================================================

// Shell descriptor and routes (consumed by scope:web app.routes.ts)
export { DASH_MODULE_DESCRIPTOR, DASH_MODULE_PERMISSION_MAP, DASH_MODULE_FEATURE_FLAGS } from './lib/dash.descriptor';
export { DASH_ROUTES } from './lib/dash.routes';

// Nav config (shell sidebar / breadcrumb registration)
export { DASH_NAV_CONFIG } from './lib/dash-nav-config';

// Facades (scope:web may inject directly for inter-module composition)
export { DashHomeFacade } from './lib/dash.facade';
export { NotificationsFacade } from './lib/notifications.facade';

// View-model types (shared with scope:web for composition)
export type {
  DashSummaryResponse,
  PatientSummaryVm,
  CaregiverVm,
  DeviceVm,
  LatestEventVm,
  NotificationEnvelope,
  UnacknowledgedResponse,
  HomeScreenVm,
  NotificationsScreenVm,
  AppError,
  DeviceStatus,
  EventUrgencia,
  EventFeedback,
  NotificationTier,
  ScreenStatus,
} from './lib/dash.types';

// Providers factory
export { provideDash } from './lib/dash.providers';
export type { DashProviderConfig } from './lib/dash.providers';

// Access layer — DI tokens
export { DASH_AUTH_SESSION, DASH_ACCESS_DECISION } from './lib/access/dash-access-tokens';
export type { DashAuthSession, DashAccessDecision } from './lib/access/dash-access-tokens';

// Cross-module injection tokens (INT-FE-DASH-001 / INT-FE-DASH-002)
export { DASH_ACTIVE_EVENT_TOKEN, DASH_DEVICE_BADGE_TOKEN } from './lib/access/dash-access-tokens';
export type { ActiveEventVm, DeviceBadgeVm } from './lib/access/dash-access-tokens';

// Feature components exported for scope:web top-level routes
// (EntradaComponent is rendered OUTSIDE the shell chrome — registered in app.routes.ts directly)
export { EntradaComponent } from './lib/feature-entrada/entrada.component';

// Port tokens (needed by app.config.ts to override adapters in tests)
export { DASH_SUMMARY_PORT } from './lib/api/dash-summary.port';
export { DASH_NOTIFICATIONS_PORT } from './lib/api/dash-notifications.port';
export type { DashSummaryPort } from './lib/api/dash-summary.port';
export type { DashNotificationsPort } from './lib/api/dash-notifications.port';

// Shell integration + lifecycle bootstrap (FE-DASH-018, FE-DASH-019)
export { DashShellIntegrationService } from './lib/integration/dash-shell-integration.service';
export type {
  DashShellRegistry,
  DashBreadcrumbItem as DashIntegrationBreadcrumbItem,
  DashBreadcrumbPort,
} from './lib/integration/dash-shell-integration.service';
export { dashInit, dashDestroy } from './lib/dash.bootstrap';

// Navigation breadcrumbs (FE-DASH-020)
export { DASH_BREADCRUMBS } from './lib/dash-nav-config';
export type { DashBreadcrumbItem } from './lib/dash-nav-config';
