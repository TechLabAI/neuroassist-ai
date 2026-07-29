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
