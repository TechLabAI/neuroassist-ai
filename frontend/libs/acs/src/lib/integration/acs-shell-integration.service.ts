import { Injectable, Signal, Type, inject } from '@angular/core';
import { AcsTenantFacade } from '../acs-tenant.facade';
import { AcsCaregiverFacade } from '../acs-caregiver.facade';
import { ACS_MODULE_DESCRIPTOR } from '../acs.descriptor';
import { ACS_NAV_CONFIG } from '../acs-nav-config';
import { TenantSetupComponent } from '../feature-tenant-setup/tenant-setup.component';
import { AcsEmpresaContextService } from '../api/acs-empresa-context.service';

// =============================================================================
// AcsShellIntegrationService — SPEC-06 (shell integration) / INT-FE-ACS-002
//
// Bridges ACS into the shell without ACS reaching into scope:web.
// Consumed by scope:web shell to:
//   1. Bootstrap ACS on app load (load active tenant + caregiver roster)
//   2. Register module descriptor + nav config with the shell module registry
//   3. Contribute ACS breadcrumb segments to the shell breadcrumb trail
//   4. Provide the onboarding wizard step composition for the shell wizard host
//   5. Expose read-only signals for shell-level rendering (topbar, coverage badge)
//
// nx-boundary rule: scope:web MUST NOT import scope:acs internals directly.
// =============================================================================

// ---------------------------------------------------------------------------
// Minimal shell contracts (stubs until scope:web promotes ShellRegistry / BreadcrumbService)
// TODO[scaffold-web]: replace with real ShellRegistry from scope:web when available.
// ---------------------------------------------------------------------------

/** Breadcrumb segment pushed to the shell breadcrumb trail. */
export interface AcsBreadcrumbItem {
  label: string;
  url?: string;
}

/** Minimal push-only breadcrumb contract that the shell BreadcrumbService must satisfy. */
export interface AcsBreadcrumbPort {
  push(items: AcsBreadcrumbItem[]): void;
}

/** Minimal shell module registry contract.
 * TODO[scaffold-web]: replace with real ShellRegistry from scope:web when available. */
export interface AcsShellRegistry {
  /** Register the ACS module descriptor (routes, nav config, permission map). */
  registerModule(descriptor: typeof ACS_MODULE_DESCRIPTOR): void;
  /** Register ACS navigation config (sidebar entry, order). */
  registerNavConfig(config: typeof ACS_NAV_CONFIG): void;
  /** Declare ACS as the sole writer of the active empresa context. */
  declareEmpresaWriter(moduleKey: string): void;
}

/** One step in the onboarding wizard composition. */
export interface OnboardingWizardStep {
  /** Unique step key. */
  key: string;
  /** Human-readable title (pt-BR). */
  title: string;
  /** Lazy-resolved component for the onboarding wizard host to render. */
  component: Promise<Type<unknown>>;
  /** Step index (1-based). Lower numbers appear earlier in the wizard. */
  order: number;
}

@Injectable({ providedIn: 'root' })
export class AcsShellIntegrationService {
  private readonly tenantFacade = inject(AcsTenantFacade);
  private readonly caregiverFacade = inject(AcsCaregiverFacade);
  private readonly empresaContext = inject(AcsEmpresaContextService);

  private _registered = false;
  private _breadcrumbSub: (() => void) | null = null;

  // ---------------------------------------------------------------------------
  // bootstrap — existing API kept for backward compatibility
  // Called once from AppShellComponent.ngOnInit() after provideAcs() is registered.
  // ---------------------------------------------------------------------------

  /** Load the active tenant and the caregiver roster (derives coverage state). */
  async bootstrap(activeTenantId: string): Promise<void> {
    await Promise.all([
      this.tenantFacade.load(activeTenantId),
      this.caregiverFacade.loadRoster(),
    ]);
  }

  // ---------------------------------------------------------------------------
  // INT-FE-ACS-002: register — module descriptor + nav config + empresa-writer declaration
  // ---------------------------------------------------------------------------

  /**
   * Register the ACS module with the shell module registry.
   * Idempotent — safe to call multiple times (guard by _registered flag).
   *
   * Contributes:
   *   - ACS_MODULE_DESCRIPTOR (routes, permission map, feature flags)
   *   - ACS_NAV_CONFIG (sidebar entry — "Cuidadores", order: 3)
   *   - Declares ACS as the sole writer of the active empresa context
   *
   * @param registry - Shell module registry. When scope:web ShellRegistry is not yet
   *   available, pass a no-op stub; the declaration is idempotent.
   *   TODO[scaffold-web]: replace AcsShellRegistry with real ShellRegistry from scope:web.
   */
  register(registry: AcsShellRegistry): void {
    if (this._registered) return;
    this._registered = true;

    registry.registerModule(ACS_MODULE_DESCRIPTOR);
    registry.registerNavConfig(ACS_NAV_CONFIG);
    // INT-FE-ACS-003: declare ACS as the sole writer of empresa context
    // so the shell and peer modules can resolve the source of X-Empresa-Id.
    registry.declareEmpresaWriter('acs');
  }

  // ---------------------------------------------------------------------------
  // INT-FE-ACS-002: contributeBreadcrumbs — pipe ACS nav signal to shell
  // ---------------------------------------------------------------------------

  /**
   * Connect ACS navigation state to the shell breadcrumb trail.
   * Should be called once per navigation event (or from the shell router outlet).
   *
   * Pushes the current ACS route breadcrumb (label: 'Cuidadores', url: '/acs').
   * Sub-route breadcrumbs (editor, linkage) should be pushed by their respective
   * components via the same port.
   *
   * @param port - Shell breadcrumb service. When scope:web BreadcrumbService is not
   *   yet available, pass a no-op implementation.
   *   TODO[scaffold-web]: replace AcsBreadcrumbPort with real BreadcrumbService.
   */
  contributeBreadcrumbs(port: AcsBreadcrumbPort): void {
    port.push([{ label: ACS_NAV_CONFIG.label, url: ACS_NAV_CONFIG.route }]);
  }

  // ---------------------------------------------------------------------------
  // INT-FE-ACS-002: onboardingWizardComposition — declare wizard steps
  // ---------------------------------------------------------------------------

  /**
   * Returns the ACS-owned onboarding wizard steps for the shell wizard host.
   *
   * ACS contributes two steps:
   *   Step 1: TenantSetupComponent — provision the empresa (mandatory first step)
   *   Step 5: CaregiverLinkStepComponent — link at least one caregiver to each patient
   *
   * NOTE: CaregiverLinkStepComponent is the simplified onboarding variant of
   * PatientLinkageComponent. Until it is scaffolded separately, TenantSetupComponent
   * is used as a placeholder for step 5 as well.
   * TODO[FE-ACS-021]: replace step 5 placeholder with real CaregiverLinkStepComponent.
   *
   * The host wizard renders these components in order; steps from other modules fill
   * in between (PAC, EDGE, etc.). ACS steps MUST NOT import PAC/EDGE/GEST scope.
   */
  onboardingWizardComposition(): OnboardingWizardStep[] {
    return [
      {
        key: 'acs-tenant-setup',
        title: 'Configurar unidade de cuidado',
        component: Promise.resolve(TenantSetupComponent as unknown as Type<unknown>),
        order: 1,
      },
      {
        key: 'acs-caregiver-link',
        // TODO[FE-ACS-021]: replace with CaregiverLinkStepComponent
        title: 'Vincular cuidador aos pacientes',
        component: Promise.resolve(TenantSetupComponent as unknown as Type<unknown>),
        order: 5,
      },
    ];
  }

  // ---------------------------------------------------------------------------
  // Existing read-model signals (kept for backward compatibility)
  // ---------------------------------------------------------------------------

  /** Active tenant view-model signal (null until bootstrap completes). */
  get tenant(): Signal<import('../acs.types').TenantVm | null> {
    return this.tenantFacade.tenant;
  }

  /** Active empresa id signal — null until tenant provisioning completes. */
  get empresaId(): Signal<string | null> {
    return this.empresaContext.empresaId;
  }

  /** Coverage for a specific patient (derived client-side). */
  coverageFor(pacienteId: string) {
    return this.caregiverFacade.coverageFor(pacienteId);
  }

  /** Whether ACS has been registered with the shell registry. */
  isRegistered(): boolean {
    return this._registered;
  }
}
