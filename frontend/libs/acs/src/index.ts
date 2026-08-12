// =============================================================================
// @neuroassist/acs — public API barrel
// Imported by scope:web shell (app.routes.ts, app.config.ts) and shell host pages.
// Modules must NEVER import each other's feature libs directly (nx-boundaries).
// =============================================================================

// Shell descriptor and routes (consumed by scope:web app.routes.ts)
export { ACS_MODULE_DESCRIPTOR } from './lib/acs.descriptor';
export { ACS_ROUTES } from './lib/acs.routes';

// Facades (scope:web may inject directly; other modules must not)
export { AcsTenantFacade } from './lib/acs-tenant.facade';
export { AcsCaregiverFacade } from './lib/acs-caregiver.facade';

// View-model types (shared with scope:web for composition)
export type {
  TenantVm,
  CuidadorVm,
  CoverageVm,
  CaregiverDraft,
  TenantTipo,
  CuidadorPapel,
  CuidadorStatus,
} from './lib/acs.types';

// Integration service (scope:web shell injects this)
export { AcsShellIntegrationService } from './lib/integration/acs-shell-integration.service';

// Providers factory
export { provideAcs } from './lib/acs.providers';
export type { AcsProviderConfig } from './lib/acs.providers';

// Port tokens (needed by app.config.ts to override adapters in tests)
export { ACS_TENANT_PORT } from './lib/api/acs-tenant.port';
export { ACS_CUIDADOR_PORT } from './lib/api/acs-cuidador.port';
export { ACS_LINK_PORT } from './lib/api/acs-link.port';
export type { AcsTenantPort, TenantDto, TenantCreateDto } from './lib/api/acs-tenant.port';
export type { AcsCuidadorPort, CuidadorDto, CuidadorCreateDto } from './lib/api/acs-cuidador.port';
export type { AcsLinkPort, CaregiverLinkDto } from './lib/api/acs-link.port';

// Composed step components (scope:web onboarding wizard mounts these)
export { TenantSetupComponent } from './lib/feature-tenant-setup/tenant-setup.component';
export { CaregiverEditorComponent } from './lib/feature-caregiver-editor/caregiver-editor.component';
