import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
  computed,
  OnInit,
  Type,
} from '@angular/core';
import { RouterLink } from '@angular/router';
import { NgComponentOutlet } from '@angular/common';
import {
  AcsTenantFacade,
  AcsCaregiverFacade,
  AcsShellIntegrationService,
  ACS_ACCESS_DECISION,
  ACS_MODULE_DESCRIPTOR,
} from '@neuroassist/acs';
import { EdgeShellIntegrationService } from '@neuroassist/edge';
import { PacPacienteFacade, PatientRegisterComponent } from '@neuroassist/pac';

// =============================================================================
// OnboardingWizardPageComponent — scope:web wizard shell (ACS owns this page)
// Promotes design-reference onboarding.component.ts to production.
// ACS owns the wizard orchestration; each step composites a module's step feature.
//
// Step 1: Tenant setup        → AcsTenantFacade.create() / .retryProvisioning()
//                               TRÊS estados: sem empresa · provisionando · ativa
// Step 2: Patient register    → PatientRegisterComponent (PAC-owned, composto aqui)
//
// nx-boundary do passo 2: importado do BARRIL @neuroassist/pac, que é a mesma
// porta por onde `PacPacienteFacade` já entrava nesta página. Não usa
// NgComponentOutlet como o passo 3 porque o PAC não publica um resolvedor de
// slot (não há `resolvePatientRegisterSlot()`), e sem resolvedor o outlet teria
// de importar o tipo do componente do mesmo jeito — só que perdendo a checagem
// de template. O barril do PAC declara este componente como sendo justamente
// para o wizard do ACS.
// Step 3: Edge device pairing → EdgeShellIntegrationService.resolvePairingSlot()
//                               (rendered via NgComponentOutlet — INT-FE-EDGE-003)
// Step 4: First command       → link to /pac (PAC catalog)
// Step 5: Invite caregiver    → AcsCaregiverFacade.invite()
// Step 6: Caregiver link      → AcsShellIntegrationService.resolveCaregiverLinkSlot()
//                               (PatientLinkageComponent via NgComponentOutlet)
//
// nx-boundary: EdgeShellIntegrationService imported from @neuroassist/edge barrel only
// (no scope:edge internal imports in scope:web). DevicePairingPageComponent type is
// never imported here — obtained lazily via resolvePairingSlot() at runtime.
//
// Route: /onboarding (scope:web, outside shell — uses AppComponent directly)
// Wired by app.routes.ts as a standalone loadComponent (no shell layout).
// =============================================================================

type Pareamento = 'idle' | 'pareando' | 'pareado';

@Component({
  selector: 'app-onboarding-wizard-page',
  standalone: true,
  imports: [RouterLink, NgComponentOutlet, PatientRegisterComponent],
  template: `
    <div class="mx-auto max-w-6xl p-8">
      <header class="mb-7">
        <h1 class="font-display text-2xl font-bold tracking-tight text-ink">Configuração inicial</h1>
        <p class="mt-1 text-sm text-ink-soft">Passo único, conduzido pelo condutor técnico — não faz parte do uso diário da cuidadora.</p>
      </header>

      <div class="grid grid-cols-1 gap-6 lg:grid-cols-[2fr_1fr]">
        <!-- Coluna principal: passos do wizard -->
        <div class="flex flex-col gap-5">
          <!-- Step 1: Unidade de cuidado (ACS) -->
          <div class="rounded-2xl border border-line bg-surface p-5">
            <div class="mb-3 flex items-center gap-2">
              <span class="grid size-6 place-items-center rounded-full bg-brand-subtle text-xs font-bold text-brand">1</span>
              <h2 class="font-display text-sm font-semibold text-ink">Unidade de cuidado</h2>
              <!-- A insígnia verde exige 'isActive', e não a mera existência do
                   registro. Antes bastava 'tenant()' ser truthy: uma empresa que
                   voltou 202 — ou seja, cujo schema ainda está sendo provisionado
                   — aparecia com check verde e nome, e o assistente seguia como se
                   estivesse pronta. O mesmo defeito se repetia no checklist e em
                   podeConcluir(). -->
              @if (empresaAtiva()) {
                <span class="ml-auto text-xs font-medium text-success-ink" data-testid="tenant-active">
                  <span class="material-symbols-outlined text-[14px] align-middle">check_circle</span>
                  {{ tenantFacade.tenant()!.nome }} — empresa ativa
                </span>
              }
            </div>

            @if (empresaAtiva()) {
              <p class="text-sm text-ink-soft">Empresa confirmada pelo backend. Siga para o cadastro do paciente.</p>
            } @else if (tenantFacade.tenant()) {
              <!-- SEGUNDO estado: o registro existe e o provisionamento não
                   terminou. Provisionar schema é uma saga no acs-service, que
                   responde 202; 'AcsTenantFacade.provisioning()' já representava
                   isso e nenhuma tela o desenhava. -->
              <div class="flex flex-col gap-3">
                <div
                  class="flex items-center gap-2 text-sm text-brand"
                  role="status"
                  data-testid="tenant-provisioning"
                >
                  <span class="material-symbols-outlined animate-spin text-[18px]" aria-hidden="true">progress_activity</span>
                  Provisionando {{ tenantFacade.tenant()!.nome }}… a confirmação vem do backend.
                </div>

                <!-- A faixa só aparece DEPOIS de uma retentativa que não resolveu.
                     Mostrá-la já na primeira espera transformaria o caminho normal
                     da saga (que leva segundos) num aviso de falha. -->
                @if (retryTentado() && !tenantFacade.loading()) {
                  <p
                    class="rounded-lg bg-warning-bg px-3 py-2 text-xs text-warning-ink"
                    role="alert"
                    data-testid="tenant-provisioning-failed"
                  >
                    Empresa ainda não confirmada — o provisionamento segue pendente.
                  </p>
                }

                @if (tenantFacade.error()) {
                  <p class="text-xs text-error-ink" role="alert">{{ tenantFacade.error() }}</p>
                }

                <div>
                  <button
                    class="rounded-xl border border-line px-4 py-2 text-sm font-semibold text-ink hover:bg-surface-2 disabled:opacity-50"
                    [disabled]="tenantFacade.loading()"
                    (click)="retentarProvisionamento()"
                    data-testid="tenant-retry"
                  >
                    {{ tenantFacade.loading() ? 'Tentando…' : 'Tentar novamente' }}
                  </button>
                </div>
              </div>
            } @else {
              <div class="flex gap-3">
                <input
                  type="text"
                  class="flex-1 rounded-lg border border-line bg-surface-2 px-3 py-2 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-brand"
                  [value]="tenantNome()"
                  (input)="tenantNome.set($any($event.target).value)"
                  placeholder="Nome da família ou clínica"
                />
                <!-- Criar EMPRESA é operação de plataforma, não de coordenadora:
                     'TenantController:45' exige 'acs.tenant.provisionar', que o
                     'RoleAuthorityPolicy' não concede a papel humano nenhum
                     (E2E-029 tirou-a do COORDENADOR_CLINICA; ela vive no cliente
                     de serviço 'platform-ops'). Sem esta trava o botão ficava
                     vivo para quem chegasse aqui, e o 403 só apareceria depois do
                     clique — no dia em que sair do mock. -->
                <button
                  class="rounded-xl bg-brand px-4 py-2 text-sm font-semibold text-brand-ink hover:bg-brand-strong disabled:opacity-50"
                  [disabled]="!podeCriarEmpresa() || !tenantNome().trim() || tenantFacade.loading()"
                  (click)="criarTenant()"
                >
                  {{ tenantFacade.loading() ? 'Criando…' : 'Criar' }}
                </button>
              </div>
              @if (!podeCriarEmpresa()) {
                <p class="mt-2 text-xs text-ink-soft" data-testid="tenant-write-denied">
                  Criar a empresa é do administrador de plataforma — a clínica é registrada pela equipe técnica antes deste passo.
                </p>
              }
              @if (tenantFacade.error()) {
                <p class="mt-2 text-xs text-error-ink">{{ tenantFacade.error() }}</p>
              }
            }
          </div>

          <!-- Step 2: Paciente (PAC) — PatientRegisterComponent composto em lugar
               do parágrafo estático que havia aqui.

               O parágrafo dizia "O registro do paciente é gerido pelo módulo PAC"
               com um link para /pac, e isso FECHAVA UM CICLO SEM SAÍDA: sem
               paciente registrado, /pac cai no pacTenantPatientGuard, que manda
               para /onboarding, que manda de volta para /pac. Não havia
               superfície nenhuma no produto por onde cadastrar o primeiro
               paciente — o componente que faz isso existe desde sempre, está no
               barril do PAC e não era montado em lugar algum. O item "Paciente
               registrado" da lista de progresso lê pacFacade.hasPatient(), que
               por ação de quem usa nunca podia virar verdadeiro.

               O componente traz o próprio cartão, o próprio número do passo e o
               próprio h2 "Paciente" — por isso substitui o bloco inteiro em vez
               de ser aninhado nele. Não tem h1, então não repete o título da
               página como o passo 3 repetia (é o que a entrada "composto" do
               pareamento existe para evitar). -->
          <pac-patient-register />

          <!-- Step 3: Dispositivo edge (EDGE) — INT-FE-EDGE-003 -->
          <!-- DevicePairingPageComponent mounted via NgComponentOutlet (no direct import, nx-boundary preserved) -->
          <!-- pacienteHint wired from PacPacienteFacade.patient().id via ngComponentOutletInputs -->
          <div class="rounded-2xl border border-line bg-surface p-5">
            <div class="mb-3 flex items-center gap-2">
              <span class="grid size-6 place-items-center rounded-full bg-brand-subtle text-xs font-bold text-brand">3</span>
              <h2 class="font-display text-sm font-semibold text-ink">Dispositivo edge</h2>
              @if (pareamentoEfetivo() === 'pareado') {
                <span class="ml-auto text-xs font-medium text-success-ink">
                  <span class="material-symbols-outlined text-[14px] align-middle">check_circle</span>
                  Pareado
                </span>
              }
            </div>
            @if (pareamentoEfetivo() !== 'pareado') {
              @if (pairingComponentType(); as compType) {
                <!-- DevicePairingPageComponent composed in place via slot (SPEC-06 §3) -->
                <ng-container
                  [ngComponentOutlet]="compType"
                  [ngComponentOutletInputs]="pairingInputs()" />
              } @else {
                <div class="flex items-center gap-2 text-sm text-ink-soft">
                  <span class="material-symbols-outlined animate-spin text-[18px]">progress_activity</span>
                  Carregando módulo de pareamento…
                </div>
              }
            } @else {
              <div class="flex items-center gap-2 text-sm text-success-ink">
                <span class="material-symbols-outlined text-[18px]">check_circle</span>
                Dispositivo pareado com sucesso.
              </div>
            }
          </div>

          <!-- Step 4: Primeiro comando (PAC) -->
          <div class="rounded-2xl border border-line bg-surface p-5">
            <div class="mb-3 flex items-center gap-2">
              <span class="grid size-6 place-items-center rounded-full bg-brand-subtle text-xs font-bold text-brand">4</span>
              <h2 class="font-display text-sm font-semibold text-ink">Primeiro comando</h2>
            </div>
            <div class="flex items-center justify-between gap-3">
              <p class="text-sm text-ink-soft">Configure ao menos 1 comando (gesto → necessidade) no catálogo antes de convidar a cuidadora.</p>
              <a routerLink="/pac" class="flex-shrink-0">
                <button class="rounded-xl border border-line px-3 py-1.5 text-xs font-semibold text-ink hover:bg-surface-2">
                  Ir ao catálogo
                  <span class="material-symbols-outlined text-[14px] align-middle">arrow_forward</span>
                </button>
              </a>
            </div>
          </div>

          <!-- Step 5: Convidar cuidadora (ACS) -->
          <div class="rounded-2xl border border-line bg-surface p-5">
            <div class="mb-3 flex items-center gap-2">
              <span class="grid size-6 place-items-center rounded-full bg-brand-subtle text-xs font-bold text-brand">5</span>
              <h2 class="font-display text-sm font-semibold text-ink">Convidar cuidadora</h2>
            </div>
            <div class="flex flex-col gap-3">
              <div class="flex gap-3">
                <input
                  type="text"
                  class="flex-1 rounded-lg border border-line bg-surface-2 px-3 py-2 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-brand"
                  [value]="cuidadoraNome()"
                  (input)="cuidadoraNome.set($any($event.target).value)"
                  placeholder="Nome da cuidadora"
                />
                <input
                  type="email"
                  class="flex-1 rounded-lg border border-line bg-surface-2 px-3 py-2 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-brand"
                  [value]="cuidadoraEmail()"
                  (input)="cuidadoraEmail.set($any($event.target).value)"
                  placeholder="E-mail"
                />
              </div>
              @if (caregiverFacade.error()) {
                <p class="text-xs text-error-ink">{{ caregiverFacade.error() }}</p>
              }
            </div>
          </div>

          <!-- Step 6: Vincular cuidadora ao paciente (ACS) — Passo 3 do tutorial.

               Era a lacuna entre o guia e o produto: o tutorial manda a
               coordenadora vincular a cuidadora ao paciente, e não havia
               superfície nenhuma no assistente por onde fazê-lo. O que existia
               era 'AcsShellIntegrationService.onboardingWizardComposition()'
               devolvendo 'TenantSetupComponent' de novo, sob
               'TODO[FE-ACS-021]' — e ninguém chamava aquele método.

               Composto por 'NgComponentOutlet' a partir de
               'resolveCaregiverLinkSlot()', exatamente como o passo 3 compõe o
               pareamento do EDGE: 'scope:web' nunca importa o tipo do
               componente de dentro do 'scope:acs'. -->
          <div class="rounded-2xl border border-line bg-surface p-5" data-testid="caregiver-link-step">
            <div class="mb-3 flex items-center gap-2">
              <span class="grid size-6 place-items-center rounded-full bg-brand-subtle text-xs font-bold text-brand">6</span>
              <h2 class="font-display text-sm font-semibold text-ink">Vincular cuidadora ao paciente</h2>
            </div>
            @if (caregiverLinkComponentType(); as compLink) {
              <ng-container
                [ngComponentOutlet]="compLink"
                [ngComponentOutletInputs]="caregiverLinkInputs()" />
            } @else {
              <div class="flex items-center gap-2 text-sm text-ink-soft">
                <span class="material-symbols-outlined animate-spin text-[18px]">progress_activity</span>
                Carregando módulo de vínculo…
              </div>
            }
          </div>
        </div>

        <!-- Coluna lateral: progresso + conclusão -->
        <div class="flex flex-col gap-6">
          <div class="rounded-2xl border border-line bg-surface p-5">
            <h3 class="mb-3 font-display text-sm font-semibold text-ink">Progresso</h3>
            <div class="flex flex-col gap-2.5">
              @for (item of checklist(); track item.label) {
                <!--
                  Gancho estável para o e2e. Ancorar em texto não serve aqui: o
                  ícone é uma LIGADURA, então o texto do item é
                  "check_circle Paciente registrado" — e um seletor que casasse
                  com o rótulo exato não achava o item, enquanto um que casasse
                  por pedaço achava também os ancestrais. data-done diz o
                  estado sem depender de qual ligadura está desenhada.
                -->
                <div
                  class="flex items-center gap-2.5 text-sm"
                  data-testid="checklist-item"
                  [attr.data-item]="item.label"
                  [attr.data-done]="item.done">
                  <span class="material-symbols-outlined text-[18px]"
                        [class]="item.done ? 'text-success' : 'text-ink-faint'">
                    {{ item.done ? 'check_circle' : 'radio_button_unchecked' }}
                  </span>
                  <span [class]="item.done ? 'text-ink' : 'text-ink-soft'">{{ item.label }}</span>
                </div>
              }
            </div>
          </div>

          <div class="rounded-2xl border border-line bg-surface p-5">
            <p class="text-xs text-ink-soft">Conclua todos os passos ao lado para liberar o painel diário da cuidadora.</p>
            <div class="mt-4">
              <button
                class="w-full rounded-xl bg-brand py-2.5 text-sm font-semibold text-brand-ink hover:bg-brand-strong disabled:opacity-50"
                [disabled]="!podeConcluir()"
                (click)="concluir()"
              >
                Concluir configuração
              </button>
            </div>
            @if (concluido()) {
              <p class="mt-3 flex items-center gap-1.5 text-sm font-medium text-success-ink">
                <span class="material-symbols-outlined text-[18px]">check_circle</span>
                Configuração concluída
              </p>
            }
          </div>
        </div>
      </div>
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OnboardingWizardPageComponent implements OnInit {
  readonly tenantFacade = inject(AcsTenantFacade);
  readonly caregiverFacade = inject(AcsCaregiverFacade);
  protected readonly pacFacade = inject(PacPacienteFacade);

  // Composição do passo 6 (vínculo) — mesma porta do EDGE no passo 3.
  private readonly acsIntegration = inject(AcsShellIntegrationService);

  /**
   * Decisão de acesso do ACS. `app.config.ts` a traduz pelo
   * MODULE_PERMISSION_MAP (chave desconhecida NEGA).
   *
   * <p>Ausência do provedor também NEGA — ao contrário do bypass Wave-0 dos
   * guards. Criar empresa é a operação mais privilegiada desta tela; um
   * ambiente que esqueceu de ligar a decisão não deve ganhar a capacidade de
   * presente.
   */
  private readonly access = inject(ACS_ACCESS_DECISION, { optional: true });

  // INT-FE-EDGE-003: EDGE composition via EdgeShellIntegrationService (no DeviceFacade import).
  // scope:web only sees EdgeShellIntegrationService from @neuroassist/edge barrel.
  private readonly edgeIntegration = inject(EdgeShellIntegrationService);

  readonly tenantNome = signal('');
  readonly cuidadoraNome = signal('');
  readonly cuidadoraEmail = signal('');
  readonly pareamento = signal<Pareamento>('idle');
  readonly concluido = signal(false);

  /**
   * DevicePairingPageComponent type, resolved lazily via resolvePairingSlot().
   * Typed as Type<object> to avoid importing DevicePairingPageComponent in scope:web
   * (nx-boundary: no direct scope:edge component import in scope:web).
   */
  readonly pairingComponentType = signal<Type<object> | null>(null);

  /** PatientLinkageComponent, resolvido por slot — ver a nota do passo 6. */
  readonly caregiverLinkComponentType = signal<Type<object> | null>(null);

  /**
   * Uma retentativa de provisionamento já foi disparada nesta sessão de tela.
   *
   * <p>Separa "esperando pela primeira vez" de "tentei de novo e continua
   * pendente" — só o segundo caso merece faixa de aviso.
   */
  readonly retryTentado = signal(false);

  /** Inputs for NgComponentOutlet — pacienteHint bound from active patient (SPEC-06 §3). */
  readonly pairingInputs = computed<Record<string, unknown>>(() => ({
    pacienteHint: this.pacFacade.patient()?.id ?? null,
    // Aqui ele é um PEDAÇO da página, não a página: sem isto o passo 3 trazia o
    // próprio <h1>, e /onboarding ficava com dois títulos de nível 1.
    composto: true,
  }));

  /**
   * Entradas do passo 6. `pacienteId` vem do paciente ativo do PAC porque em
   * `/onboarding` não existe `:pacienteId` na rota — é o único jeito de a
   * superfície de vínculo saber de quem se trata. `composto` suprime o <h1> e o
   * contêiner próprios do componente, pelo mesmo motivo do passo 3.
   */
  readonly caregiverLinkInputs = computed<Record<string, unknown>>(() => ({
    pacienteId: this.pacFacade.patient()?.id ?? null,
    composto: true,
  }));

  /**
   * A empresa está PRONTA — não apenas registrada.
   *
   * <p>`isActive` é `provisioningStatus === 'active'`. Trocar isto de volta por
   * `!!tenant()` faz uma empresa em provisionamento passar por pronta em três
   * lugares desta tela.
   */
  readonly empresaAtiva = computed(() => this.tenantFacade.tenant()?.isActive === true);

  /** Quem pode provisionar empresa — ver a nota no botão "Criar". */
  readonly podeCriarEmpresa = computed(
    () => this.access?.can(ACS_MODULE_DESCRIPTOR.permissions.tenantWrite) ?? false,
  );

  /**
   * Detects pairing completion via two signals:
   *   1. connectivity.paired — updated after next liveness poll (authoritative)
   *   2. pairingPhase === 'paired' — immediate (after first heartbeat confirmed by PairingFacade)
   * This ensures the checklist updates without waiting for the next poll cycle.
   */
  readonly dispositivoExistente = computed(
    () =>
      this.edgeIntegration.connectivityReadModel()().paired ||
      this.edgeIntegration.pairingPhase()() === 'paired',
  );

  readonly pareamentoEfetivo = computed<Pareamento>(() =>
    this.dispositivoExistente() ? 'pareado' : this.pareamento(),
  );

  readonly checklist = computed(() => [
    { label: 'Unidade de cuidado', done: this.empresaAtiva() },
    { label: 'Paciente registrado', done: this.pacFacade.hasPatient() },
    { label: 'Dispositivo pareado', done: this.pareamentoEfetivo() === 'pareado' },
    { label: 'Cuidadora convidada', done: this.cuidadoraEmail().trim().length > 0 },
  ]);

  readonly podeConcluir = computed(() =>
    this.empresaAtiva() &&
    this.pareamentoEfetivo() === 'pareado' &&
    this.cuidadoraEmail().trim().length > 0,
  );

  /**
   * Resolve the pairing component type at init so the slot is ready to render
   * before the user reaches step 3 (SPEC-06 §3, INT-FE-EDGE-003).
   * resolvePairingSlot() returns Promise.resolve(DevicePairingPageComponent) — synchronous in mock.
   */
  ngOnInit(): void {
    this.edgeIntegration.resolvePairingSlot().then((comp) => {
      this.pairingComponentType.set(comp as Type<object>);
    });
    this.acsIntegration.resolveCaregiverLinkSlot().then((comp) => {
      this.caregiverLinkComponentType.set(comp as Type<object>);
    });
  }

  async criarTenant(): Promise<void> {
    await this.tenantFacade.create({ nome: this.tenantNome().trim(), tipo: 'familia' });
  }

  /**
   * Reexecuta a saga de provisionamento da empresa já registrada.
   *
   * <p>`AcsTenantFacade.retryProvisioning()` existia desde sempre e nenhuma tela
   * o chamava — a capacidade estava escrita e inalcançável.
   */
  async retentarProvisionamento(): Promise<void> {
    const atual = this.tenantFacade.tenant();
    if (!atual) return;
    this.retryTentado.set(true);
    await this.tenantFacade.retryProvisioning(atual.id);
  }

  /**
   * Called by host when the (paired) output is emitted from DevicePairingPageComponent.
   * Advances the onboarding state (SPEC-06 §3 / INT-FE-EDGE-003).
   * Note: since NgComponentOutlet does not wire outputs natively, this method is also
   * triggered reactively via dispositivoExistente when pairingPhase() === 'paired'.
   */
  onDevicePaired(_outcome: unknown): void {
    this.pareamento.set('pareado');
  }

  protected async concluir(): Promise<void> {
    if (this.cuidadoraEmail().trim()) {
      await this.caregiverFacade.invite({
        nome: this.cuidadoraNome().trim() || 'Cuidadora',
        email: this.cuidadoraEmail().trim(),
        papel: 'cuidadora',
      });
    }
    this.concluido.set(true);
  }
}
