// =============================================================================
// @neuroassist/evnt — public API barrel
// Imported by scope:web shell (app.routes.ts, app.config.ts) and shell host pages.
// Modules must NEVER import each other's feature libs directly (nx-boundaries).
// =============================================================================

// Shell descriptor and routes (consumed by scope:web app.routes.ts)
export { EVNT_MODULE_DESCRIPTOR } from './lib/evnt.descriptor';
export { EVNT_ROUTES } from './lib/evnt.routes';
export { evntBootstrap } from './lib/evnt.bootstrap';

// Navigation config (FE-EVNT-006) — standalone for Shell sidebar/breadcrumb registration
export { EVNT_NAV_CONFIG } from './lib/evnt.nav-config';

// Facade (scope:web may inject directly; other modules must not)
export { EventDeliveryFacade } from './lib/evnt.facade';

// View-model types (shared with scope:web for composition)
export type { EventoVm, AlarmVm, RoutineFeedVm, EventDetailVm, DeliveryMode } from './lib/evnt.types';

// Integration service (scope:web shell injects this)
export { EvntShellIntegrationService } from './lib/integration/evnt-shell-integration.service';

// Providers factory (P6-A, P6-H)
export { provideEvnt, provideEvntForProduction, EVNT_E2E_BYPASS } from './lib/evnt.providers';
export type { EvntProviderConfig } from './lib/evnt.providers';

// HTTP interceptor (P6-A) — exported so app.config.ts can register it globally if needed
export { evntHttpInterceptorFn } from './lib/api/evnt-http.interceptor';

// Feature flags (P6-D) — override token exported for test environments
export { EvntFeatureFlagsService, EVNT_FEATURE_FLAGS_OVERRIDE } from './lib/api/evnt-feature-flags.service';
export type { EvntFeatureFlags } from './lib/api/evnt-feature-flags.service';

// Port tokens (needed by app.config.ts to override adapters)
export { EVNT_CLIENT_PORT } from './lib/api/evnt-client.port';
export type { EvntClientPort, EventoDto, ActiveEventsDto, SseFrame } from './lib/api/evnt-client.port';

// SSE parser (P6-B) — exported for testing and reuse
export { parseSseLine, createSseBuffer } from './lib/api/evnt-sse-parser';
export type { SseBuffer } from './lib/api/evnt-sse-parser';

// Error mapper (P6-C) — exported for consumers who receive raw port errors
export { mapProblemDetailToAppError } from './lib/api/evnt-error-mapper';

// Error classifier helper (P6-G) — detail page error routing
export { classifyDetailError } from './lib/feature-routine/event-detail-page/event-detail-page.component';

// Alarm overlay (scope:web shell renders this via ShellOutletRegistry)
export { AlarmOverlayComponent } from './lib/feature-alarm/alarm-overlay.component';

// UI leaf components (FE-EVNT-018) — shared within scope:evnt feature libs
export { UrgencyBadgeComponent } from './lib/ui/urgency-badge/urgency-badge.component';
export { EventTimestampComponent } from './lib/ui/event-timestamp/event-timestamp.component';
export { formatTimestamp } from './lib/ui/event-timestamp/event-timestamp.component';

// P7 UX Polish components (FE-EVNT-022, FE-EVNT-024)
export { EvntFeedSkeletonComponent } from './lib/ui/feed-skeleton/evnt-feed-skeleton.component';
export { EvntDegradedModeBannerComponent } from './lib/ui/degraded-banner/evnt-degraded-mode-banner.component';

// Feature-routine presentational components (FE-EVNT-014, FE-EVNT-015)
export { RoutineNoticeComponent } from './lib/feature-routine/routine-notice/routine-notice.component';
export { EventListComponent } from './lib/feature-routine/event-list/event-list.component';

// Feature-routine smart page (FE-EVNT-013)
export { EventFeedPageComponent } from './lib/feature-routine/event-feed-page.component';

// Feature-routine P4 components (FE-EVNT-017: FeedbackVerdictControl + ConfirmationTrail)
export { FeedbackVerdictControl } from './lib/feature-routine/feedback-verdict-control/feedback-verdict-control.component';
export { ConfirmationTrailComponent } from './lib/feature-routine/confirmation-trail/confirmation-trail.component';

// Feature-routine smart detail page (FE-EVNT-016)
export { EventDetailPageComponent } from './lib/feature-routine/event-detail-page/event-detail-page.component';

// Telemetry service (P8 — FE-EVNT-025) — zero PII, follows ACS pattern
export { EvntTelemetryService } from './lib/telemetry/evnt-telemetry.service';
