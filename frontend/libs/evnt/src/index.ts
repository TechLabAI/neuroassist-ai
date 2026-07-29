// =============================================================================
// @neuroassist/evnt — public API barrel
// Imported by scope:web shell (app.routes.ts, app.config.ts) and shell host pages.
// Modules must NEVER import each other's feature libs directly (nx-boundaries).
// =============================================================================

// Shell descriptor and routes (consumed by scope:web app.routes.ts)
export { EVNT_MODULE_DESCRIPTOR } from './lib/evnt.descriptor';
export { EVNT_ROUTES } from './lib/evnt.routes';
export { evntBootstrap } from './lib/evnt.bootstrap';

// Facade (scope:web may inject directly; other modules must not)
export { EventDeliveryFacade } from './lib/evnt.facade';

// View-model types (shared with scope:web for composition)
export type { EventoVm, AlarmVm, RoutineFeedVm, EventDetailVm, DeliveryMode } from './lib/evnt.types';

// Integration service (scope:web shell injects this)
export { EvntShellIntegrationService } from './lib/integration/evnt-shell-integration.service';

// Providers factory
export { provideEvnt } from './lib/evnt.providers';
export type { EvntProviderConfig } from './lib/evnt.providers';

// Port tokens (needed by app.config.ts to override adapters)
export { EVNT_CLIENT_PORT } from './lib/api/evnt-client.port';
export type { EvntClientPort, EventoDto, ActiveEventsDto, SseFrame } from './lib/api/evnt-client.port';

// Alarm overlay (scope:web shell renders this via ShellOutletRegistry)
export { AlarmOverlayComponent } from './lib/feature-alarm/alarm-overlay.component';
