import { Injectable, Signal, Type, computed, inject } from '@angular/core';
import { AcsTenantFacade } from '../acs-tenant.facade';
import { AcsCaregiverFacade } from '../acs-caregiver.facade';
import { ACS_MODULE_DESCRIPTOR } from '../acs.descriptor';
import { ACS_NAV_CONFIG } from '../acs-nav-config';
import { PatientLinkageComponent } from '../feature-patient-linkage/patient-linkage.component';
import { CaregiverEditorComponent } from '../feature-caregiver-editor/caregiver-editor.component';
import type {
  OnboardingWizardStep,
  OnboardingWizardContributor,
} from '@neuroassist/shared/contracts';
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
// O tipo `OnboardingWizardStep` MUDOU DE CASA: foi para
// `@neuroassist/shared/contracts`. Enquanto morava aqui, só o ACS podia declarar
// um passo — e o assistente tinha de conhecer PAC e EDGE por dentro para montar
// os deles. Com o contrato em `shared`, cada módulo declara o seu.

@Injectable({ providedIn: 'root' })
export class AcsShellIntegrationService implements OnboardingWizardContributor {
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
   * ACS contribui UM passo: o 6, `PatientLinkageComponent` — vincular ao menos
   * uma cuidadora ao paciente.
   *
   * <p><b>O passo 1 saiu.</b> Ele entregava o `TenantSetupComponent`, que foi
   * REMOVIDO: era um componente completo, com teste próprio, montado em lugar
   * nenhum — e quem realmente desenha o passo da empresa é o próprio assistente,
   * que aprendeu os três estados de provisionamento. Manter duas telas para a
   * mesma coisa, uma delas invisível, garante que a invisível apodreça sem que
   * ninguém perceba.
   *
   * <p><b>Nota:</b> este método continua SEM CHAMADOR. O assistente monta o passo
   * de vínculo direto por {@link resolveCaregiverLinkSlot}. Fica porque é o
   * contrato de composição declarado (INT-FE-ACS-002) e porque o passo que ele
   * descreve é real; se seguir sem uso, é candidato à mesma remoção.
   *
   * O passo 6 DEIXOU DE SER PLACEHOLDER. Ele devolvia `TenantSetupComponent` de
   * novo, sob `TODO[FE-ACS-021]: replace step 5 placeholder with real
   * CaregiverLinkStepComponent` — um componente que nunca existiu. Não havia
   * "variante simplificada" a escrever: a superfície de vínculo é a
   * `PatientLinkageComponent`, que já existe, já deriva cobertura (ADR-005) e já
   * bloqueia o vínculo entre empresas. O que faltava nela era poder ser montada
   * fora da própria rota — resolvido pelas entradas `pacienteId` e `composto`.
   *
   * The host wizard renders these components in order; steps from other modules fill
   * in between (PAC, EDGE, etc.). ACS steps MUST NOT import PAC/EDGE/GEST scope.
   */
  onboardingWizardComposition(): OnboardingWizardStep[] {
    return [
      {
        // O CONVITE deixou de ser formulário embutido no assistente.
        //
        // Eram dois <input> escritos no template do shell chamando
        // `AcsCaregiverFacade` direto — e o convite só era efetivamente enviado
        // no botão "Concluir configuração", lá no fim. Ou seja, o passo não
        // fazia o que dizia fazer no momento em que dizia.
        //
        // Agora é o `CaregiverEditorComponent`, o MESMO da rota
        // /acs/cuidadores/novo: validação, papel e estado de erro vêm de graça,
        // e não há uma segunda superfície de convite para envelhecer sozinha.
        key: 'acs-caregiver-invite',
        title: 'Convidar cuidadora',
        component: this.resolveCaregiverInviteSlot() as Promise<Type<unknown>>,
        order: 5,
        // `composto` suprime o <h1>, o contêiner e o botão "Cancelar" — e, o que
        // mais importa, impede a NAVEGAÇÃO para /acs no sucesso, que ejetaria a
        // pessoa do assistente depois de uma ação bem-sucedida.
        inputs: () => ({ composto: true }),
      },
      {
        key: 'acs-caregiver-link',
        title: 'Vincular cuidadora ao paciente',
        component: this.resolveCaregiverLinkSlot() as Promise<Type<unknown>>,
        // Depois de convidar a cuidadora (passo 5): não há a quem vincular antes.
        order: 6,
        // `pacienteId` vem do CONTEXTO porque em /onboarding não há `:pacienteId`
        // na rota, e o ACS não pode (nem deve) importar o PAC para descobri-lo.
        // `composto` suprime o <h1> e o contêiner do componente: aqui ele é um
        // pedaço da página, não a página.
        inputs: (contexto) => ({ pacienteId: contexto.pacienteId, composto: true }),
      },
    ];
  }

  /**
   * Entrega a superfície de VÍNCULO para quem compõe o assistente.
   *
   * <p>Espelha `EdgeShellIntegrationService.resolvePairingSlot()`, e existe pelo
   * mesmo motivo: `scope:web` monta o componente por `NgComponentOutlet` sem
   * jamais importar o tipo de dentro do `scope:acs`. O `Promise` é o formato
   * combinado — hoje resolve na hora, e é o gancho por onde o componente vira
   * `import()` preguiçoso sem mexer em quem chama.
   */
  resolveCaregiverLinkSlot(): Promise<Type<PatientLinkageComponent>> {
    return Promise.resolve(PatientLinkageComponent);
  }

  /**
   * Entrega a superfície de CONVITE para quem compõe o assistente.
   *
   * <p>É o componente da rota /acs/cuidadores/novo, e de propósito: o convite do
   * assistente e o convite do elenco são a mesma operação, com as mesmas
   * validações. Duas superfícies para a mesma coisa garantem que uma delas
   * envelheça calada.
   */
  resolveCaregiverInviteSlot(): Promise<Type<CaregiverEditorComponent>> {
    return Promise.resolve(CaregiverEditorComponent);
  }

  /**
   * Há ao menos uma cuidadora ativa na empresa.
   *
   * <p>É o que o assistente precisa saber para marcar "Cuidadora convidada" no
   * progresso. Antes ele lia o próprio campo de e-mail — isto é, o item ficava
   * verde assim que a pessoa DIGITAVA, antes de qualquer convite ter sido
   * enviado. Progresso falso, e o convite só saía no botão de concluir.
   *
   * <p>Agora o critério é o estado real do elenco, e quem o observa não precisa
   * injetar `AcsCaregiverFacade`.
   */
  readonly temCuidadoraAtiva: Signal<boolean> = computed(
    () => this.caregiverFacade.activeCaregivers().length > 0,
  );

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
