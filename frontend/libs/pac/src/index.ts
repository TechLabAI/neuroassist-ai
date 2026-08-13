// =============================================================================
// @neuroassist/pac — public API barrel
// Imported by scope:web shell pages (app.routes.ts, app.config.ts) and
// scope:acs onboarding wizard (PatientRegisterComponent).
//
// scope:pac and scope:shared only — zero scope:gest imports (nx-boundary).
// GestureLiveTestComponent is composed at scope:web via ng-content slot
// in CommandEditorComponent (PAC never imports scope:gest directly — ADR-002).
// =============================================================================

// Shell descriptor and routes (consumed by scope:web app.routes.ts)
export { PAC_MODULE_DESCRIPTOR } from './lib/pac.descriptor';
export { PAC_ROUTES } from './lib/pac.routes';

// Providers factory
export { providePac } from './lib/pac.providers';
export type { PacProviderConfig } from './lib/pac.providers';

// Facades (scope:web may inject for cross-module read; PAC self-injects)
export { PacComandoFacade } from './lib/pac-comando.facade';
export { PacPacienteFacade } from './lib/pac-paciente.facade';
export { TiposGestoReadFacade } from './lib/tipos-gesto-read.facade';
export type { ComandoRowVM, EditorVM } from './lib/pac-comando.facade';
export type { PacienteVM } from './lib/pac-paciente.facade';
export type { TipoGestoVM } from './lib/tipos-gesto-read.facade';

// Port tokens (needed by app.config.ts to override adapters in tests)
export { PAC_PACIENTE_PORT } from './lib/api/paciente.port';
export type { PacPacientePort, Paciente } from './lib/api/paciente.port';
export { PAC_COMANDO_PORT } from './lib/api/comando.port';
export type { PacComandoPort, Comando, ComandoEstado, SyncState } from './lib/api/comando.port';
export { TIPOS_GESTO_READ_PORT } from './lib/api/tipos-gesto-read.port';
export type { TiposGestoReadPort, TipoGesto } from './lib/api/tipos-gesto-read.port';

// Feature components exported for direct embedding (scope:acs onboarding wizard)
export { PatientRegisterComponent } from './lib/feature-patient-register/patient-register.component';
export { CommandCatalogComponent } from './lib/feature-command-catalog/command-catalog.component';
export { CommandEditorComponent } from './lib/feature-command-editor/command-editor.component';

// Route guards — exported so scope:web can compose its own route definitions
// around CommandEditorComponent (e.g. the [slot=live-test] host wrapper) while
// still enforcing the same access rules PAC_ROUTES applies internally.
export { pacAuthGuard } from './lib/access/pac-auth.guard';
export { pacTenantPatientGuard } from './lib/access/pac-tenant-patient.guard';
export { pacCommandWriteGuard } from './lib/access/pac-command-write.guard';

// ---------------------------------------------------------------------------
// P8 Observability — PAC-FE-022
// ---------------------------------------------------------------------------

// PacTelemetryService — FE telemetry events for PAC module actions
export { PacTelemetryService } from './lib/telemetry/pac-telemetry.service';
export type { PacLifecycleAction } from './lib/telemetry/pac-telemetry.service';

// ---------------------------------------------------------------------------
// P7 UX Polish — PAC-FE-019, PAC-FE-020, PAC-FE-021
// ---------------------------------------------------------------------------

// Presentational UI components (PAC-FE-019)
export { CommandRowComponent } from './lib/ui/command-row.component';
export { UrgencyBadgeComponent } from './lib/ui/urgency-badge.component';
export type { UrgenciaType } from './lib/ui/urgency-badge.component';
export { SensitivitySelectorComponent, SENSIBILIDADE_LABELS } from './lib/ui/sensitivity-selector.component';
export type { SensibilidadeType } from './lib/ui/sensitivity-selector.component';
export { SyncStatusBadgeComponent } from './lib/ui/sync-status-badge.component';
export type { SyncStateDisplay } from './lib/ui/sync-status-badge.component';

// SyncStatusBannerComponent — ADR-005 honest state (PAC-FE-020)
export { SyncStatusBannerComponent } from './lib/ui/sync-status-banner.component';
export type { SyncStateBanner } from './lib/ui/sync-status-banner.component';

// Error surface pipeline — 12 ProblemDetail types (PAC-FE-021)
export { PacErrorHandler } from './lib/error-surface/pac-error.handler';
export type { ErrorContext } from './lib/error-surface/pac-error.handler';
export { PacFeedbackService } from './lib/error-surface/pac-feedback.service';
export type { ToastNotification, BannerNotification, ModalNotification } from './lib/error-surface/pac-feedback.service';
export { mapProblemDetailToAppError, extractErrorCode } from './lib/error-surface/pac-problem-detail.mapper';
export type { ProblemDetail } from './lib/error-surface/pac-problem-detail.mapper';
