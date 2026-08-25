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

// Detection contract (GEST-FE-009)
export { DetectionContractService } from './lib/api/detection-contract.service';
export type { DetectionContractDescriptor } from './lib/api/detection-contract.service';

// Multi-empresa wiring token (P1 — GEST-FE-009 / GEST-INT-002)
export { EMPRESA_CONTEXT_TOKEN } from './lib/api/eligibility-http.adapter';

// Estação do paciente — P2 / GEST-FE-027 / ADR-008
// Store de identidade de dispositivo (scope:web e scope:gest estacao only)
export { DeviceSessionStore, AVISO_CREDENCIAL_DISPOSITIVO } from './lib/estacao/device-session.store';
export type { EstadoSessao } from './lib/estacao/device-session.store';
export { DeviceAuthHttpAdapter, DeviceAuthErro } from './lib/estacao/device-auth-http.adapter';
export { DEVICE_AUTH_PORT } from './lib/estacao/device-auth.port';
export type { DeviceTokenRequest, DeviceAuthResult } from './lib/estacao/device-auth.port';

// ADR-007 — motor de detecção no browser
export { EdgeDetectionBrowserAdapter } from './lib/api/edge-detection-browser.adapter';
export { GestureDetector, ENGINE_FEATURE, DEFAULT_ASSET_PATHS } from './lib/detection/gesture-detector';
export type { GestureFrame, DetectorAssetPaths, GestureDetectorCallbacks } from './lib/detection/gesture-detector';
export { CameraPreferenceService } from './lib/detection/camera-preference.service';
export type { CameraOption } from './lib/detection/camera-preference.service';

// Estação do paciente — P3 / GEST-FE-028/029 / ADR-008
// Endpoint migrado para GET /api/v1/edge/profile (EDGE-022 resolvido).
export { DetectionProfileStore } from './lib/estacao/detection-profile.store';
export type { PrimitivoResolvido, ProfileDegradacao } from './lib/estacao/detection-profile.store';
export { DetectionProfileHttpAdapter } from './lib/estacao/detection-profile-http.adapter';
export { DETECTION_PROFILE_PORT } from './lib/estacao/detection-profile.port';
export type {
  DetectionProfilePort,
  DetectionProfileDto,
  PrimitivoDeteccao,
  ThresholdsTable,
  SensibilidadeNivel,
  OrigemPerfil,
  ProfileResult,
  ProfileNotModified,
} from './lib/estacao/detection-profile.port';
export { isNotModified } from './lib/estacao/detection-profile.port';

// Estação do paciente — P4 / GEST-FE-030 / ADR-008
// Rota /estacao: laço always-on, guard de sessão de dispositivo, relato de gesto.
export { EstacaoComponent } from './lib/estacao/estacao.component';
export type { EstacaoEstado } from './lib/estacao/estacao.component';
export { estacaoDeviceGuard } from './lib/estacao/estacao.guard';
export { ReportIngressHttpAdapter } from './lib/estacao/report-ingress-http.adapter';
export { ReportIngressPort } from './lib/estacao/report-ingress.port';
export type { ReportRequest, ReportResponse } from './lib/estacao/report-ingress.port';
