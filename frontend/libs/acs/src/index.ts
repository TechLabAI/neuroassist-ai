// =============================================================================
// @neuroassist/acs — public API barrel
// Imported by scope:web shell (app.routes.ts, app.config.ts) and shell host pages.
// Modules must NEVER import each other's feature libs directly (nx-boundaries).
// =============================================================================

// Shell descriptor and routes (consumed by scope:web app.routes.ts)
export { ACS_MODULE_DESCRIPTOR, MODULE_PERMISSION_MAP, MODULE_FEATURE_FLAGS } from './lib/acs.descriptor';
export { ACS_ROUTES } from './lib/acs.routes';

// Nav config (shell sidebar / breadcrumb registration)
export { ACS_NAV_CONFIG } from './lib/acs-nav-config';

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
export type {
  AcsBreadcrumbItem,
  AcsBreadcrumbPort,
  AcsShellRegistry,
  OnboardingWizardStep,
} from './lib/integration/acs-shell-integration.service';

// Empresa context — ACS is the sole writer; scope:web and peers read via this service
export { AcsEmpresaContextService } from './lib/api/acs-empresa-context.service';

// HTTP interceptor — wire in app.config.ts: provideHttpClient(withInterceptors([acsHttpInterceptorFn]))
// SKIP_EMPRESA_ID_HEADER: set on tenant provisioning requests to omit X-Empresa-Id
export { acsHttpInterceptorFn, SKIP_EMPRESA_ID_HEADER } from './lib/api/acs-http.interceptor';

// Providers factory
export { provideAcs } from './lib/acs.providers';
export type { AcsProviderConfig } from './lib/acs.providers';

// Telemetry (INT-FE-ACS-004)
export { AcsTelemetryService } from './lib/telemetry/acs-telemetry.service';

// Access layer — DI tokens (Wave-0 bypass pattern; provide real services to enforce RBAC)
export { ACS_AUTH_SESSION, ACS_ACCESS_DECISION } from './lib/access/acs-access-tokens';
export type { AcsAuthSession, AcsAccessDecision } from './lib/access/acs-access-tokens';

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

// Guards de acesso — exportados para que rotas FORA do módulo ACS possam usar a
// mesma tranca. A `/onboarding` vive em `apps/neuroassist-ai-web` e exercita a
// capacidade de cadastrar cuidadora; sem esta exportação ela só sabia exigir
// sessão, e sessão qualquer pessoa autenticada tem.
export {
  acsCoordinatorGuard,
  acsCaregiverWriteGuard,
  acsLinkWriteGuard,
} from './lib/access/acs.guard';
