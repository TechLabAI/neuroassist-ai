import {
  Component,
  ChangeDetectionStrategy,
  inject,
  signal,
  computed,
  OnInit,
  Type,
} from '@angular/core';
import { NgComponentOutlet } from '@angular/common';
import {
  AcsTenantFacade,
  AcsShellIntegrationService,
  ACS_ACCESS_DECISION,
  ACS_MODULE_DESCRIPTOR,
} from '@neuroassist/acs';
import { EdgeShellIntegrationService } from '@neuroassist/edge';
import { PacShellIntegrationService } from '@neuroassist/pac';
import type {
  OnboardingWizardContext,
  OnboardingWizardContributor,
  OnboardingWizardStep,
} from '@neuroassist/shared/contracts';

// =============================================================================
// OnboardingWizardPageComponent — o HOSPEDEIRO do assistente de configuração
// inicial. Rota /onboarding (scope:web, fora do shell).
//
// -----------------------------------------------------------------------------
// DECISÃO 1 — cada módulo declara o seu passo; esta página só hospeda.
// -----------------------------------------------------------------------------
//
// Até aqui esta página CONHECIA OS MÓDULOS POR DENTRO: importava o
// `PatientRegisterComponent` do barril do PAC e o montava com o número e o
// título escritos à mão no template; resolvia o pareamento por um slot do EDGE
// e escrevia as entradas dele aqui; e tinha um bloco de template por passo.
// Cada passo novo era mais um módulo que o shell precisava conhecer.
//
// Agora cada módulo implementa `OnboardingWizardContributor` e DECLARA o passo
// que é dele (chave, título, componente, ordem, entradas). Esta página reúne o
// que os contribuidores declararam, ORDENA por `order` e renderiza por
// `NgComponentOutlet` com `inputs(contexto)` — sem importar componente de módulo
// nenhum.
//
// TODOS os cinco passos numerados são declarados pelos módulos: PAC declara o
// paciente e o primeiro comando, EDGE o dispositivo, ACS o convite e o vínculo.
// O template desta página não tem mais um único bloco por passo — tem UM cartão
// genérico, repetido.
//
// O que ela ainda sabe, e por quê:
//   - QUEM são os contribuidores (injeta os três serviços de integração). É a
//     lista de módulos participantes; num registro dinâmico isso viraria um
//     multi-provider, e o contrato já permite essa evolução sem tocar aqui.
//   - O CONTEXTO (`pacienteId`), que é justamente o que nenhum módulo sozinho
//     sabe. Ele vem do serviço de integração do PAC, não da facade: o shell não
//     injeta mais `PacPacienteFacade` nem `AcsCaregiverFacade`.
//
// -----------------------------------------------------------------------------
// DECISÃO 2 — a empresa é CONTEXTO, não passo numerado.
// -----------------------------------------------------------------------------
//
// A empresa aparece no alto, como "onde você está", com os três estados de
// provisionamento e a retentativa quando for o caso. A sequência numerada começa
// no PACIENTE.
//
// Razão: `acs.tenant.provisionar` não é dada a papel humano nenhum
// (RoleAuthorityPolicy, E2E-029 — vive no cliente de serviço platform-ops).
// Enquanto a empresa era o passo 1, o assistente abria com um item que a pessoa
// à frente dele NÃO PODE executar, e o checklist começava por algo fora do
// alcance dela. Quem chega aqui já tem a clínica: ela é cenário, não tarefa.
// Por isso saiu do checklist e saiu de `podeConcluir()`.
//
// -----------------------------------------------------------------------------
// O QUE AINDA ESTÁ ACOPLADO (dito de propósito, para não passar por resolvido)
// -----------------------------------------------------------------------------
//   - A EMPRESA. O cartão de contexto no alto é desenhado aqui e chama
//     `AcsTenantFacade` direto (criar, retentar, ler os três estados). Não é
//     passo, então não passa pelo contrato de composição — e o contrato hoje só
//     descreve passos. Enquanto for assim, o shell conhece o ACS por dentro
//     para esta parte.
//   - O passo do EDGE tem um tratamento especial no template (o distintivo
//     "Pareado" e a troca do conteúdo quando o pareamento conclui), o que faz o
//     hospedeiro reconhecer uma chave de módulo pelo nome. É a última coisa que
//     ele reconhece por nome.
//   - O CHECKLIST é do hospedeiro e sabe quais três coisas contam. Ele lê o
//     estado pelos serviços de integração, não pelas facades — mas a LISTA de
//     itens continua escrita aqui, e um módulo novo não a estende sozinho.
// =============================================================================

type Pareamento = 'idle' | 'pareando' | 'pareado';

/**
 * Um passo pronto para renderizar: o que o módulo declarou, já com o componente
 * resolvido e as entradas calculadas para o contexto corrente.
 */
interface PassoRenderizado {
  readonly key: string;
  readonly title: string;
  readonly order: number;
  /** null enquanto o `Promise` do módulo não resolveu — ou para passos embutidos. */
  readonly component: Type<object> | null;
  readonly inputs: Record<string, unknown>;
}

@Component({
  selector: 'app-onboarding-wizard-page',
  standalone: true,
  imports: [NgComponentOutlet],
  template: `
    <div class="mx-auto max-w-6xl p-8">
      <header class="mb-7">
        <h1 class="font-display text-2xl font-bold tracking-tight text-ink">Configuração inicial</h1>
        <p class="mt-1 text-sm text-ink-soft">Passo único, conduzido pelo condutor técnico — não faz parte do uso diário da cuidadora.</p>
      </header>

      <!-- ===================================================================
           CONTEXTO — "onde você está". NÃO é passo numerado.

           Criar empresa exige 'acs.tenant.provisionar', que o RoleAuthorityPolicy
           não concede a papel humano nenhum (E2E-029 a moveu para o cliente de
           serviço 'platform-ops'). Enquanto isto era o passo 1, o assistente
           abria com uma tarefa que quem está diante dele não pode executar.
           Quem chega aqui já tem a clínica.

           Os três estados continuam desenhados: sem empresa (formulário, para o
           condutor de plataforma), provisionando (a saga responde 202) e ativa.
           A insígnia verde exige 'isActive', e não a mera existência do registro.
           =================================================================== -->
      <section
        class="mb-6 rounded-2xl border border-line bg-surface-2 p-5"
        data-testid="empresa-contexto"
        aria-label="Unidade de cuidado"
      >
        <div class="mb-3 flex items-center gap-2">
          <span class="material-symbols-outlined text-[18px] text-ink-faint" aria-hidden="true">apartment</span>
          <h2 class="font-display text-sm font-semibold text-ink">Unidade de cuidado</h2>
          @if (empresaAtiva()) {
            <span class="ml-auto text-xs font-medium text-success-ink" data-testid="tenant-active">
              <span class="material-symbols-outlined text-[14px] align-middle">check_circle</span>
              {{ tenantFacade.tenant()!.nome }} — empresa ativa
            </span>
          }
        </div>

        @if (empresaAtiva()) {
          <p class="text-sm text-ink-soft">Empresa confirmada pelo backend. A configuração abaixo é feita dentro dela.</p>
        } @else if (tenantFacade.tenant()) {
          <!-- SEGUNDO estado: o registro existe e o provisionamento não terminou.
               Provisionar schema é uma saga no acs-service, que responde 202. -->
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
                 Mostrá-la já na primeira espera transformaria o caminho normal da
                 saga (que leva segundos) num aviso de falha. -->
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
                class="rounded-xl border border-line px-4 py-2 text-sm font-semibold text-ink hover:bg-surface disabled:opacity-50"
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
              class="flex-1 rounded-lg border border-line bg-surface px-3 py-2 text-sm text-ink focus:outline-none focus:ring-2 focus:ring-brand"
              [value]="tenantNome()"
              (input)="tenantNome.set($any($event.target).value)"
              placeholder="Nome da família ou clínica"
            />
            <!-- Ver a nota do bloco: a authority não é de papel humano. Sem esta
                 trava o botão ficava vivo para quem chegasse aqui, e o 403 só
                 apareceria depois do clique — no dia em que sair do mock. -->
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
              Criar a empresa é do administrador de plataforma — a clínica é registrada pela equipe técnica antes desta tela.
            </p>
          }
          @if (tenantFacade.error()) {
            <p class="mt-2 text-xs text-error-ink">{{ tenantFacade.error() }}</p>
          }
        }
      </section>

      <div class="grid grid-cols-1 gap-6 lg:grid-cols-[2fr_1fr]">
        <!-- ===================================================================
             SEQUÊNCIA NUMERADA — um cartão por passo, na ordem declarada.

             O número vem da POSIÇÃO, não de 'order': assim, um módulo que deixa
             de declarar renumera os seguintes em vez de deixar buraco na conta.
             =================================================================== -->
        <div class="flex flex-col gap-5">
          @for (passo of passos(); track passo.key; let i = $index) {
            <div
              class="rounded-2xl border border-line bg-surface p-5"
              data-testid="wizard-step"
              [attr.data-step-key]="passo.key"
              [attr.data-step-number]="i + 1"
            >
              <div class="mb-3 flex items-center gap-2">
                <span class="grid size-6 place-items-center rounded-full bg-brand-subtle text-xs font-bold text-brand">{{ i + 1 }}</span>
                <h2 class="font-display text-sm font-semibold text-ink">{{ passo.title }}</h2>
                @if (passo.key === 'edge-device-pairing' && pareamentoEfetivo() === 'pareado') {
                  <span class="ml-auto text-xs font-medium text-success-ink">
                    <span class="material-symbols-outlined text-[14px] align-middle">check_circle</span>
                    Pareado
                  </span>
                }
              </div>

              <!-- O hospedeiro NAO conhece mais nenhum passo por dentro: ele
                   monta o componente que o modulo declarou, com as entradas que
                   o modulo calculou a partir do contexto. -->
              @if (passo.key === 'edge-device-pairing' && pareamentoEfetivo() === 'pareado') {
                <div class="flex items-center gap-2 text-sm text-success-ink">
                  <span class="material-symbols-outlined text-[18px]">check_circle</span>
                  Dispositivo pareado com sucesso.
                </div>
              } @else if (passo.component; as tipo) {
                <ng-container
                  [ngComponentOutlet]="tipo"
                  [ngComponentOutletInputs]="passo.inputs" />
              } @else {
                <div class="flex items-center gap-2 text-sm text-ink-soft" data-testid="passo-carregando">
                  <span class="material-symbols-outlined animate-spin text-[18px]">progress_activity</span>
                  Carregando o passo…
                </div>
              }
            </div>
          }
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
                  por pedaço achava também os ancestrais. data-done diz o estado
                  sem depender de qual ligadura está desenhada.
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

  /**
   * Os módulos que contribuem passos.
   *
   * <p>É a única coisa que o hospedeiro sabe sobre eles: que existem e que
   * implementam `OnboardingWizardContributor`. Não sabe quantos passos cada um
   * traz, nem quais, nem onde entram na sequência.
   */
  private readonly acsIntegration = inject(AcsShellIntegrationService);
  private readonly edgeIntegration = inject(EdgeShellIntegrationService);
  private readonly pacIntegration = inject(PacShellIntegrationService);

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

  readonly tenantNome = signal('');
  readonly pareamento = signal<Pareamento>('idle');
  readonly concluido = signal(false);

  /** O que os contribuidores declararam, já ordenado por `order`. */
  private readonly declarados = signal<readonly OnboardingWizardStep[]>([]);

  /**
   * Componentes já resolvidos, por chave de passo.
   *
   * <p>Mapa, e não um campo por passo, porque o hospedeiro não conhece as chaves
   * de antemão — ele descobre quais existem lendo o que os módulos declararam.
   */
  private readonly tiposResolvidos = signal<ReadonlyMap<string, Type<object>>>(new Map());

  /**
   * Uma retentativa de provisionamento já foi disparada nesta sessão de tela.
   *
   * <p>Separa "esperando pela primeira vez" de "tentei de novo e continua
   * pendente" — só o segundo caso merece faixa de aviso.
   */
  readonly retryTentado = signal(false);

  /**
   * O que o hospedeiro sabe e os módulos não.
   *
   * <p>`computed`, e não um objeto fixo: o paciente ativo só existe depois que o
   * passo do paciente termina, e as entradas dos passos seguintes precisam
   * enxergar essa mudança.
   */
  readonly contexto = computed<OnboardingWizardContext>(() => ({
    pacienteId: this.pacIntegration.pacienteAtivoId(),
  }));

  /**
   * A sequência final: o que os módulos declararam mais os passos que esta
   * página ainda desenha, tudo ordenado por `order`.
   *
   * <p>Os embutidos entram na MESMA lista, com `order` próprio, em vez de serem
   * intercalados no template — do contrário a ordem seria decidida em dois
   * lugares e um passo declarado nunca poderia se pôr entre eles.
   */
  readonly passos = computed<readonly PassoRenderizado[]>(() => {
    const contexto = this.contexto();
    const tipos = this.tiposResolvidos();

    return this.declarados().map((passo) => ({
      key: passo.key,
      title: passo.title,
      order: passo.order,
      component: tipos.get(passo.key) ?? null,
      inputs: passo.inputs ? passo.inputs(contexto) : {},
    }));
  });

  /**
   * A empresa está PRONTA — não apenas registrada.
   *
   * <p>`isActive` é `provisioningStatus === 'active'`. Trocar isto de volta por
   * `!!tenant()` faz uma empresa em provisionamento passar por pronta.
   */
  readonly empresaAtiva = computed(() => this.tenantFacade.tenant()?.isActive === true);

  /** Quem pode provisionar empresa — ver a nota no botão "Criar". */
  readonly podeCriarEmpresa = computed(
    () => this.access?.can(ACS_MODULE_DESCRIPTOR.permissions.tenantWrite) ?? false,
  );

  /**
   * Detecta a conclusão do pareamento por dois sinais:
   *   1. connectivity.paired — atualizado no próximo poll de liveness (autoritativo)
   *   2. pairingPhase === 'paired' — imediato (primeiro heartbeat confirmado)
   */
  readonly dispositivoExistente = computed(
    () =>
      this.edgeIntegration.connectivityReadModel()().paired ||
      this.edgeIntegration.pairingPhase()() === 'paired',
  );

  readonly pareamentoEfetivo = computed<Pareamento>(() =>
    this.dispositivoExistente() ? 'pareado' : this.pareamento(),
  );

  /**
   * O checklist NÃO abre mais por "Unidade de cuidado".
   *
   * <p>Era um item que a pessoa diante da tela não pode executar (a authority de
   * provisionar não é de papel humano). Um checklist que abre com algo fora do
   * alcance de quem o lê ensina a ignorá-lo.
   */
  readonly checklist = computed(() => [
    { label: 'Paciente registrado', done: this.pacIntegration.temPaciente() },
    { label: 'Dispositivo pareado', done: this.pareamentoEfetivo() === 'pareado' },
    // O critério mudou junto com o passo. Antes era "o campo de e-mail desta
    // página não está vazio" — ou seja, o item ficava verde ao DIGITAR, e o
    // convite só era enviado lá no botão de concluir. Agora é o estado real do
    // elenco, lido pela porta do ACS.
    { label: 'Cuidadora convidada', done: this.acsIntegration.temCuidadoraAtiva() },
  ]);

  /**
   * `empresaAtiva()` SAIU da condição, pela mesma razão do checklist: a empresa
   * é pré-condição de plataforma, não tarefa desta pessoa. Prendê-la aqui fazia
   * o botão de concluir depender de algo que ela não tem como destravar.
   */
  readonly podeConcluir = computed(
    () => this.pareamentoEfetivo() === 'pareado' && this.acsIntegration.temCuidadoraAtiva(),
  );

  /**
   * Reúne o que cada módulo declarou e resolve os componentes.
   *
   * <p>A ordenação acontece aqui uma vez (a declaração não muda em tempo de
   * execução); as ENTRADAS, essas sim, são recalculadas a cada mudança de
   * contexto, em `passos()`.
   */
  ngOnInit(): void {
    const contribuidores: readonly OnboardingWizardContributor[] = [
      this.pacIntegration,
      this.edgeIntegration,
      this.acsIntegration,
    ];

    const declarados = contribuidores
      .flatMap((contribuidor) => contribuidor.onboardingWizardComposition())
      .sort((a, b) => a.order - b.order);

    this.declarados.set(declarados);

    for (const passo of declarados) {
      passo.component.then((tipo) => {
        this.tiposResolvidos.update((atual) => {
          const proximo = new Map(atual);
          proximo.set(passo.key, tipo as Type<object>);
          return proximo;
        });
      });
    }
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
   * Chamado quando o pareamento conclui. `NgComponentOutlet` não liga saídas
   * nativamente, então o caminho reativo é `dispositivoExistente()`; este método
   * fica como entrada explícita para testes e para quem compuser de outro jeito.
   */
  onDevicePaired(_desfecho: unknown): void {
    this.pareamento.set('pareado');
  }

  /**
   * Fecha a configuração.
   *
   * <p>Ele CONVIDAVA a cuidadora aqui — o formulário embutido só juntava nome e
   * e-mail, e a escrita acontecia neste botão, um passo inteiro depois de a
   * pessoa achar que já tinha convidado. O convite agora é feito pelo passo do
   * ACS, no momento em que a pessoa clica em "Convidar"; a este botão sobrou o
   * que ele sempre deveria ter feito: registrar que acabou.
   */
  protected concluir(): void {
    this.concluido.set(true);
  }
}
