// =============================================================================
// @neuroassist/gest — public API barrel
// Imported by scope:web shell pages (via GestShellIntegrationService) and the
// thin /gest dev host. scope:pac and scope:acs MUST NOT import from here
// directly — they use GestShellIntegrationService.resolveLiveTestSlot() instead
// to preserve the nx-boundary (GEST-INT-002).
// =============================================================================

// Shell descriptor and routes (consumed by scope:web app.routes.ts)
export { GEST_MODULE_DESCRIPTOR } from './lib/gest.descriptor';
export { GEST_ROUTES } from './lib/gest.routes';
export { gestBootstrap } from './lib/gest.bootstrap';

// Composition component (scope:web only — not for scope:pac/scope:acs)
export { GestureLiveTestComponent } from './lib/feature-live-test/gesture-live-test.component';

// Integration service (scope:web injects this; PAC/ACS call resolveLiveTestSlot)
export { GestShellIntegrationService } from './lib/integration/gest-shell-integration.service';

// Facade (scope:web and thin host may inject directly; PAC goes via component @Input)
export { GestureTestFacade } from './lib/gest.facade';

// Providers factory
export { provideGest } from './lib/gest.providers';
export type { GestProviderConfig } from './lib/gest.providers';

// Port tokens (needed by app.config.ts to override adapters)
export { EDGE_DETECTION_PORT } from './lib/api/edge-detection.port';
export type { EdgeDetectionPort } from './lib/api/edge-detection.port';
export { ELIGIBILITY_PORT } from './lib/api/eligibility.port';
export type { EligibilityPort } from './lib/api/eligibility.port';

// ADR-007 — motor de detecção no browser
export { EdgeDetectionBrowserAdapter } from './lib/api/edge-detection-browser.adapter';
export { GestureDetector, TIPO_GESTO, DEFAULT_ASSET_PATHS } from './lib/detection/gesture-detector';
export type { GestureFrame, DetectorAssetPaths, GestureDetectorCallbacks } from './lib/detection/gesture-detector';
export { CameraPreferenceService } from './lib/detection/camera-preference.service';
export type { CameraOption } from './lib/detection/camera-preference.service';
