import { Component, Input, computed, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import {
  AcsTenantFacade,
  AcsShellIntegrationService,
  ACS_ACCESS_DECISION,
  ACS_MODULE_DESCRIPTOR,
} from '@neuroassist/acs';
import type { TenantVm } from '@neuroassist/acs';
import { EdgeShellIntegrationService } from '@neuroassist/edge';
import { PacShellIntegrationService } from '@neuroassist/pac';
import type { OnboardingWizardStep } from '@neuroassist/shared/contracts';
import { OnboardingWizardPageComponent } from './onboarding-wizard-page.component';

// =============================================================================
// OnboardingWizardPageComponent — a composição, a empresa como contexto, os três
// estados de provisionamento e a permissão de provisionar.
//
// O QUE ESTES TESTES SEGURAM
//
//   1. COMPOSIÇÃO — o assistente monta o que os MÓDULOS declararam. Ele não
//      importa componente de módulo nenhum: se um módulo para de declarar, o
//      passo some; se a ordem muda, a sequência muda.
//   2. CONTEXTO — o `pacienteId` que só o hospedeiro sabe chega às entradas do
//      passo declarado.
//   3. EMPRESA É CONTEXTO — não é passo numerado, não está no checklist e não
//      trava `podeConcluir()`. A authority de provisionar não é de papel humano
//      (E2E-029), então o passo 1 era sempre um passo que a pessoa não pode
//      executar.
//   4. Uma empresa que voltou 202 NÃO é uma empresa pronta.
//   5. Criar empresa exige `acs.tenant.provisionar` — o botão fica desabilitado
//      até para a coordenadora.
//
// Cada afirmação tem o seu CONTROLE NEGATIVO ao lado: o estado em que a asserção
// PRECISA cair. Sem ele, uma lista que nunca enche e uma que nunca esvazia
// produzem o mesmo verde.
// =============================================================================

// --- dublês de composição --------------------------------------------------
// Precisam declarar as MESMAS entradas que os módulos passam por
// `ngComponentOutletInputs`: o outlet chama `setInput()`, e entrada não
// declarada estoura NG0303 — o que, aliás, é a prova de que as entradas
// declaradas pelo módulo chegaram intactas ao componente.

@Component({ selector: 'stub-paciente', standalone: true, template: '<p data-testid="stub-paciente">paciente</p>' })
class StubPacienteComponent {
  @Input() composto = false;
}

@Component({ selector: 'stub-pareamento', standalone: true, template: '<p data-testid="stub-pareamento">pareamento</p>' })
class StubPareamentoComponent {
  @Input() pacienteHint: string | null = null;
  @Input() composto = false;
}

@Component({ selector: 'stub-vinculo', standalone: true, template: '<p data-testid="stub-vinculo">vínculo</p>' })
class StubVinculoComponent {
  @Input() pacienteId: string | null = null;
  @Input() composto = false;
}

@Component({ selector: 'stub-comando', standalone: true, template: '<p data-testid="stub-comando">comando</p>' })
class StubPrimeiroComandoComponent {
  @Input() pacienteHint: string | null = null;
}

@Component({ selector: 'stub-convite', standalone: true, template: '<p data-testid="stub-convite">convite</p>' })
class StubConviteComponent {
  @Input() composto = false;
}

// --- dublês de estado ------------------------------------------------------

const EMPRESA_ATIVA: TenantVm = {
  id: 'tenant-1', nome: 'Família Teste', tipo: 'familia',
  provisioningStatus: 'active', isActive: true,
};
const EMPRESA_PROVISIONANDO: TenantVm = {
  id: 'tenant-1', nome: 'Família Teste', tipo: 'familia',
  provisioningStatus: 'provisioning', isActive: false,
};

// Declarações de referência — as mesmas que PAC, EDGE e ACS publicam hoje.
const PASSO_PAC: OnboardingWizardStep = {
  key: 'pac-patient-register',
  title: 'Paciente',
  component: Promise.resolve(StubPacienteComponent),
  order: 2,
  inputs: () => ({ composto: true }),
};
const PASSO_EDGE: OnboardingWizardStep = {
  key: 'edge-device-pairing',
  title: 'Dispositivo edge',
  component: Promise.resolve(StubPareamentoComponent),
  order: 3,
  inputs: (contexto) => ({ pacienteHint: contexto.pacienteId, composto: true }),
};
const PASSO_PAC_COMANDO: OnboardingWizardStep = {
  key: 'pac-first-command',
  title: 'Primeiro comando',
  component: Promise.resolve(StubPrimeiroComandoComponent),
  order: 4,
  inputs: (contexto) => ({ pacienteHint: contexto.pacienteId }),
};
const PASSO_ACS_CONVITE: OnboardingWizardStep = {
  key: 'acs-caregiver-invite',
  title: 'Convidar cuidadora',
  component: Promise.resolve(StubConviteComponent),
  order: 5,
  inputs: () => ({ composto: true }),
};
const PASSO_ACS: OnboardingWizardStep = {
  key: 'acs-caregiver-link',
  title: 'Vincular cuidadora ao paciente',
  component: Promise.resolve(StubVinculoComponent),
  order: 6,
  inputs: (contexto) => ({ pacienteId: contexto.pacienteId, composto: true }),
};

describe('OnboardingWizardPageComponent', () => {
  let fixture: ComponentFixture<OnboardingWizardPageComponent>;
  let comp: OnboardingWizardPageComponent;

  const tenantSignal = signal<TenantVm | null>(null);
  const loadingSignal = signal(false);
  const errorSignal = signal<string | null>(null);
  const pacienteSignal = signal<{ id: string } | null>(null);
  const hasPatientSignal = signal(false);

  const tenantFacade = {
    tenant: tenantSignal.asReadonly(),
    loading: loadingSignal.asReadonly(),
    error: errorSignal.asReadonly(),
    create: jest.fn().mockResolvedValue(EMPRESA_ATIVA),
    retryProvisioning: jest.fn().mockResolvedValue(undefined),
    load: jest.fn().mockResolvedValue(undefined),
  };

  const cuidadoraAtivaSignal = signal(false);

  // Os serviços de INTEGRAÇÃO substituíram as facades: o hospedeiro não injeta
  // mais `PacPacienteFacade` nem `AcsCaregiverFacade`. Trocar estes dublês por
  // facades reprovaria a montagem inteira, que é a prova de que a fronteira
  // mudou de lugar.
  const pacIntegration = {
    onboardingWizardComposition: jest.fn(() => [PASSO_PAC, PASSO_PAC_COMANDO]),
    pacienteAtivoId: computed(() => pacienteSignal()?.id ?? null),
    temPaciente: hasPatientSignal.asReadonly(),
  };
  const acsIntegration = {
    onboardingWizardComposition: jest.fn(() => [PASSO_ACS_CONVITE, PASSO_ACS]),
    temCuidadoraAtiva: cuidadoraAtivaSignal.asReadonly(),
  };
  const edgeIntegration = {
    connectivityReadModel: () => signal({ paired: false }).asReadonly(),
    pairingPhase: () => signal('idle').asReadonly(),
    onboardingWizardComposition: jest.fn(() => [PASSO_EDGE]),
  };

  const decisao = { can: jest.fn().mockReturnValue(false) };

  async function montar(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [OnboardingWizardPageComponent],
      providers: [
        provideRouter([]),
        { provide: AcsTenantFacade, useValue: tenantFacade },
        { provide: AcsShellIntegrationService, useValue: acsIntegration },
        { provide: PacShellIntegrationService, useValue: pacIntegration },
        { provide: EdgeShellIntegrationService, useValue: edgeIntegration },
        { provide: ACS_ACCESS_DECISION, useValue: decisao },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(OnboardingWizardPageComponent);
    comp = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  }

  function texto(): string {
    return fixture.nativeElement.textContent as string;
  }
  function achar(testid: string): HTMLElement | null {
    return fixture.nativeElement.querySelector(`[data-testid="${testid}"]`);
  }
  /** Os passos numerados, na ordem em que estão no DOM. */
  function passosNoDom(): { numero: string; chave: string; titulo: string }[] {
    return Array.from(
      fixture.nativeElement.querySelectorAll('[data-testid="wizard-step"]'),
    ).map((el) => {
      const passo = el as HTMLElement;
      return {
        numero: passo.getAttribute('data-step-number') ?? '',
        chave: passo.getAttribute('data-step-key') ?? '',
        titulo: passo.querySelector('h2')?.textContent?.trim() ?? '',
      };
    });
  }
  function botaoCriar(): HTMLButtonElement {
    const b = Array.from(
      fixture.nativeElement.querySelectorAll('button'),
    ).find((el) => (el as HTMLButtonElement).textContent?.trim().startsWith('Criar'));
    return b as HTMLButtonElement;
  }

  beforeEach(() => {
    TestBed.resetTestingModule();
    tenantSignal.set(null);
    loadingSignal.set(false);
    errorSignal.set(null);
    pacienteSignal.set(null);
    hasPatientSignal.set(false);
    decisao.can.mockReturnValue(false);
    jest.clearAllMocks();
    cuidadoraAtivaSignal.set(false);
    pacIntegration.onboardingWizardComposition.mockReturnValue([PASSO_PAC, PASSO_PAC_COMANDO]);
    edgeIntegration.onboardingWizardComposition.mockReturnValue([PASSO_EDGE]);
    acsIntegration.onboardingWizardComposition.mockReturnValue([PASSO_ACS_CONVITE, PASSO_ACS]);
    tenantFacade.create.mockResolvedValue(EMPRESA_ATIVA);
    tenantFacade.retryProvisioning.mockResolvedValue(undefined);
  });

  // ---------------------------------------------------------------------------
  // 1. Composição — o hospedeiro monta o que os módulos declaram
  // ---------------------------------------------------------------------------
  describe('composição dos passos', () => {
    it('pergunta a TODOS os contribuidores o que eles declaram', async () => {
      await montar();
      expect(pacIntegration.onboardingWizardComposition).toHaveBeenCalledTimes(1);
      expect(edgeIntegration.onboardingWizardComposition).toHaveBeenCalledTimes(1);
      expect(acsIntegration.onboardingWizardComposition).toHaveBeenCalledTimes(1);
    });

    it('monta os passos declarados de fato — o componente entregue pelo módulo está no DOM', async () => {
      await montar();
      expect(achar('stub-paciente')).not.toBeNull();
      expect(achar('stub-pareamento')).not.toBeNull();
      expect(achar('stub-comando')).not.toBeNull();
      expect(achar('stub-convite')).not.toBeNull();
      expect(achar('stub-vinculo')).not.toBeNull();
    });

    // TODOS os cinco passos são declarados por módulo — nenhum é desenhado pelo
    // assistente. "Primeiro comando" e "Convidar cuidadora" eram os últimos
    // embutidos: um ponteiro para /pac e um formulário chamando a facade do ACS.
    it('a sequência numerada é 1..5 e TODA ela vem dos módulos', async () => {
      await montar();
      expect(passosNoDom()).toEqual([
        { numero: '1', chave: 'pac-patient-register', titulo: 'Paciente' },
        { numero: '2', chave: 'edge-device-pairing', titulo: 'Dispositivo edge' },
        { numero: '3', chave: 'pac-first-command', titulo: 'Primeiro comando' },
        { numero: '4', chave: 'acs-caregiver-invite', titulo: 'Convidar cuidadora' },
        { numero: '5', chave: 'acs-caregiver-link', titulo: 'Vincular cuidadora ao paciente' },
      ]);
    });

    // CONTROLE NEGATIVO da composição: um módulo deixa de declarar e o passo
    // SOME — e os seguintes renumeram, em vez de deixar buraco na conta. Se o
    // hospedeiro voltasse a ter o passo escrito no template, este teste
    // reprovaria: o passo continuaria lá com o módulo calado.
    it('módulo que deixa de declarar: os passos somem e os seguintes renumeram', async () => {
      pacIntegration.onboardingWizardComposition.mockReturnValue([]);
      await montar();

      expect(achar('stub-paciente')).toBeNull();
      expect(achar('stub-comando')).toBeNull();
      expect(passosNoDom()).toEqual([
        { numero: '1', chave: 'edge-device-pairing', titulo: 'Dispositivo edge' },
        { numero: '2', chave: 'acs-caregiver-invite', titulo: 'Convidar cuidadora' },
        { numero: '3', chave: 'acs-caregiver-link', titulo: 'Vincular cuidadora ao paciente' },
      ]);
    });

    // CONTROLE NEGATIVO mais duro: com todos os módulos calados a sequência fica
    // VAZIA. Enquanto o assistente ainda desenhava dois passos por conta
    // própria, este teste encontrava dois — e era a medida exata do que faltava
    // converter.
    it('NENHUM módulo declarando: a sequência fica vazia', async () => {
      pacIntegration.onboardingWizardComposition.mockReturnValue([]);
      edgeIntegration.onboardingWizardComposition.mockReturnValue([]);
      acsIntegration.onboardingWizardComposition.mockReturnValue([]);
      await montar();
      expect(passosNoDom()).toEqual([]);
    });

    // CONTROLE NEGATIVO da ORDENAÇÃO: a mesma declaração do ACS, só que com
    // `order` menor, tem de sair na frente. Um hospedeiro que renderizasse na
    // ordem de injeção (PAC, EDGE, ACS) passaria no teste da sequência acima e
    // reprovaria aqui.
    it('a ordem vem de `order`, não da ordem em que os contribuidores foram perguntados', async () => {
      acsIntegration.onboardingWizardComposition.mockReturnValue([{ ...PASSO_ACS, order: 0 }]);
      await montar();
      expect(passosNoDom().map((p) => p.chave)).toEqual([
        'acs-caregiver-link',
        'pac-patient-register',
        'edge-device-pairing',
        'pac-first-command',
      ]);
    });

    it('um módulo pode declarar MAIS DE UM passo, e cada um entra na sua posição', async () => {
      pacIntegration.onboardingWizardComposition.mockReturnValue([
        PASSO_PAC,
        PASSO_PAC_COMANDO,
        { ...PASSO_PAC, key: 'pac-extra', title: 'Passo extra do PAC', order: 7 },
      ]);
      await montar();
      const chaves = passosNoDom().map((p) => p.chave);
      expect(chaves[0]).toBe('pac-patient-register');
      expect(chaves[chaves.length - 1]).toBe('pac-extra');
    });

    // CONTROLE NEGATIVO do slot: enquanto o `Promise` do módulo não resolve, o
    // passo mostra carregamento e NADA do conteúdo. Prova que o cartão não é um
    // invólucro que ficaria verde de qualquer jeito.
    it('enquanto o componente declarado não resolve, o passo não tem conteúdo', async () => {
      pacIntegration.onboardingWizardComposition.mockReturnValue([
        { ...PASSO_PAC, component: new Promise(() => undefined) },
      ]);
      await montar();
      expect(achar('stub-paciente')).toBeNull();
      expect(achar('passo-carregando')).not.toBeNull();
      expect(texto()).toMatch(/carregando o passo/i);
    });
  });

  // ---------------------------------------------------------------------------
  // 2. Contexto — o que só o hospedeiro sabe
  // ---------------------------------------------------------------------------
  describe('contexto repassado aos passos', () => {
    it('passa o paciente ativo às entradas calculadas pelo módulo', async () => {
      pacienteSignal.set({ id: 'pac-marcio' });
      await montar();

      const porChave = new Map(comp.passos().map((p) => [p.key, p.inputs]));
      expect(porChave.get('edge-device-pairing')).toEqual({
        pacienteHint: 'pac-marcio',
        composto: true,
      });
      expect(porChave.get('pac-first-command')).toEqual({ pacienteHint: 'pac-marcio' });
      expect(porChave.get('acs-caregiver-link')).toEqual({
        pacienteId: 'pac-marcio',
        composto: true,
      });
    });

    // CONTROLE NEGATIVO do contexto: sem paciente ativo, o que chega é `null` —
    // e não o id de antes nem `undefined`. Um hospedeiro que congelasse as
    // entradas na declaração passaria no teste acima e reprovaria aqui (ou o
    // inverso, dependendo da ordem de montagem).
    it('sem paciente ativo, o contexto entrega null', async () => {
      await montar();
      const porChave = new Map(comp.passos().map((p) => [p.key, p.inputs]));
      expect(porChave.get('edge-device-pairing')?.['pacienteHint']).toBeNull();
      expect(porChave.get('acs-caregiver-link')?.['pacienteId']).toBeNull();
      expect(comp.contexto()).toEqual({ pacienteId: null });
    });

    it('o contexto ACOMPANHA a mudança: cadastrar o paciente atualiza as entradas', async () => {
      await montar();
      expect(comp.contexto().pacienteId).toBeNull();

      pacienteSignal.set({ id: 'pac-novo' });
      fixture.detectChanges();

      const porChave = new Map(comp.passos().map((p) => [p.key, p.inputs]));
      expect(porChave.get('acs-caregiver-link')?.['pacienteId']).toBe('pac-novo');
    });

    it('passo sem `inputs` declarado recebe objeto vazio, não estoura', async () => {
      pacIntegration.onboardingWizardComposition.mockReturnValue([
        { key: PASSO_PAC.key, title: PASSO_PAC.title, order: PASSO_PAC.order, component: PASSO_PAC.component },
      ]);
      await montar();
      expect(comp.passos().find((p) => p.key === 'pac-patient-register')?.inputs).toEqual({});
      expect(achar('stub-paciente')).not.toBeNull();
    });
  });

  // ---------------------------------------------------------------------------
  // 3. A empresa é CONTEXTO, não passo numerado
  // ---------------------------------------------------------------------------
  describe('a empresa como contexto', () => {
    it('a empresa aparece numa seção de contexto, fora da sequência numerada', async () => {
      tenantSignal.set(EMPRESA_ATIVA);
      await montar();
      expect(achar('empresa-contexto')).not.toBeNull();
      expect(achar('tenant-active')).not.toBeNull();
    });

    // CONTROLE NEGATIVO da decisão 2: a empresa NÃO pode ser um passo numerado,
    // e o passo 1 tem de ser o do paciente. Reverter a empresa para passo 1
    // reprova as duas asserções.
    it('a empresa NÃO é um passo numerado — o passo 1 é o do paciente', async () => {
      tenantSignal.set(EMPRESA_ATIVA);
      await montar();
      const passos = passosNoDom();
      expect(passos.map((p) => p.titulo)).not.toContain('Unidade de cuidado');
      expect(passos[0]).toEqual({ numero: '1', chave: 'pac-patient-register', titulo: 'Paciente' });
    });

    it('o checklist não abre por "Unidade de cuidado"', async () => {
      tenantSignal.set(EMPRESA_PROVISIONANDO);
      await montar();
      const rotulos = comp.checklist().map((i) => i.label);
      expect(rotulos).not.toContain('Unidade de cuidado');
      // CONTROLE NEGATIVO: o checklist não ficou vazio — ele começa pelo item
      // que a pessoa diante da tela PODE executar.
      expect(rotulos[0]).toBe('Paciente registrado');
    });

    // O critério do item "Cuidadora convidada" mudou de dono junto com o passo.
    // Era `cuidadoraEmail().trim().length > 0` — o item ficava verde ao DIGITAR,
    // antes de qualquer convite ter sido enviado (o convite só saía no botão de
    // concluir). Agora vem do elenco real, pela porta do ACS.
    it('"Cuidadora convidada" vem do elenco do ACS, não de um campo desta tela', async () => {
      await montar();
      const item = () => comp.checklist().find((i) => i.label === 'Cuidadora convidada')?.done;
      expect(item()).toBe(false);

      cuidadoraAtivaSignal.set(true);
      fixture.detectChanges();
      expect(item()).toBe(true);
    });

    it('podeConcluir() NÃO depende da empresa: em provisionamento, com o resto pronto, libera', async () => {
      tenantSignal.set(EMPRESA_PROVISIONANDO);
      await montar();
      comp.onDevicePaired(null);
      cuidadoraAtivaSignal.set(true);
      fixture.detectChanges();
      expect(comp.podeConcluir()).toBe(true);
    });

    // CONTROLE NEGATIVO de `podeConcluir()`: tirar a empresa da condição não
    // pode ter transformado o botão num botão que libera sempre.
    it('podeConcluir() continua falso sem dispositivo pareado', async () => {
      tenantSignal.set(EMPRESA_ATIVA);
      await montar();
      cuidadoraAtivaSignal.set(true);
      fixture.detectChanges();
      expect(comp.podeConcluir()).toBe(false);
    });

    it('podeConcluir() continua falso sem cuidadora convidada', async () => {
      tenantSignal.set(EMPRESA_ATIVA);
      await montar();
      comp.onDevicePaired(null);
      fixture.detectChanges();
      expect(comp.podeConcluir()).toBe(false);
    });
  });

  // ---------------------------------------------------------------------------
  // 4. Os três estados da empresa (continuam desenhados — só mudaram de lugar)
  // ---------------------------------------------------------------------------
  describe('estado da empresa', () => {
    it('EM PROVISIONAMENTO: desenha o estado em curso e NÃO a insígnia de ativa', async () => {
      tenantSignal.set(EMPRESA_PROVISIONANDO);
      await montar();
      expect(achar('tenant-provisioning')).not.toBeNull();
      expect(achar('tenant-active')).toBeNull();
    });

    // CONTROLE NEGATIVO do item acima: com a MESMA presença de tenant, mas
    // ativo, some o estado em curso e aparece a insígnia. É o par que prova que
    // a tela olha `isActive`, e não a mera existência do registro.
    it('ATIVA: desenha a insígnia e NÃO o estado em curso', async () => {
      tenantSignal.set(EMPRESA_ATIVA);
      await montar();
      expect(achar('tenant-active')).not.toBeNull();
      expect(achar('tenant-provisioning')).toBeNull();
    });

    it('SEM EMPRESA: mostra o formulário de criação', async () => {
      await montar();
      expect(fixture.nativeElement.querySelector('input[placeholder="Nome da família ou clínica"]')).not.toBeNull();
      expect(achar('tenant-provisioning')).toBeNull();
      expect(achar('tenant-active')).toBeNull();
    });

    it('empresaAtiva() é falso para empresa em provisionamento', async () => {
      tenantSignal.set(EMPRESA_PROVISIONANDO);
      await montar();
      expect(comp.empresaAtiva()).toBe(false);
    });
  });

  // ---------------------------------------------------------------------------
  // 5. Retentativa
  // ---------------------------------------------------------------------------
  describe('retentativa de provisionamento', () => {
    it('oferece "Tentar novamente" quando a empresa não está ativa', async () => {
      tenantSignal.set(EMPRESA_PROVISIONANDO);
      await montar();
      expect(achar('tenant-retry')?.textContent).toMatch(/tentar novamente/i);
    });

    it('NÃO oferece retentativa quando a empresa está ativa (controle negativo)', async () => {
      tenantSignal.set(EMPRESA_ATIVA);
      await montar();
      expect(achar('tenant-retry')).toBeNull();
    });

    it('o clique chama retryProvisioning() com o id da empresa', async () => {
      tenantSignal.set(EMPRESA_PROVISIONANDO);
      await montar();
      (achar('tenant-retry') as HTMLButtonElement).click();
      await fixture.whenStable();
      expect(tenantFacade.retryProvisioning).toHaveBeenCalledWith('tenant-1');
    });

    it('a faixa "ainda não confirmada" só aparece DEPOIS de uma retentativa', async () => {
      tenantSignal.set(EMPRESA_PROVISIONANDO);
      await montar();
      expect(achar('tenant-provisioning-failed')).toBeNull();

      await comp.retentarProvisionamento();
      fixture.detectChanges();

      expect(achar('tenant-provisioning-failed')).not.toBeNull();
      expect(texto()).toMatch(/ainda não confirmada/i);
    });

    it('retentativa sem empresa registrada não chama a facade', async () => {
      await montar();
      await comp.retentarProvisionamento();
      expect(tenantFacade.retryProvisioning).not.toHaveBeenCalled();
    });
  });

  // ---------------------------------------------------------------------------
  // 6. Permissão de provisionar
  // ---------------------------------------------------------------------------
  describe('permissão para criar empresa', () => {
    it('consulta a decisão de acesso pela chave de tela do descritor', async () => {
      await montar();
      expect(decisao.can).toHaveBeenCalledWith(ACS_MODULE_DESCRIPTOR.permissions.tenantWrite);
    });

    it('SEM a authority o botão Criar fica desabilitado mesmo com o nome preenchido', async () => {
      decisao.can.mockReturnValue(false);
      await montar();
      comp.tenantNome.set('Empresa Proibida');
      fixture.detectChanges();
      expect(botaoCriar().disabled).toBe(true);
      expect(achar('tenant-write-denied')).not.toBeNull();
    });

    // CONTROLE NEGATIVO: com a authority, e só com ela, o botão habilita.
    it('COM a authority o botão Criar habilita', async () => {
      decisao.can.mockReturnValue(true);
      await montar();
      comp.tenantNome.set('Empresa Permitida');
      fixture.detectChanges();
      expect(botaoCriar().disabled).toBe(false);
      expect(achar('tenant-write-denied')).toBeNull();
    });

    it('a authority não substitui o nome obrigatório', async () => {
      decisao.can.mockReturnValue(true);
      await montar();
      expect(botaoCriar().disabled).toBe(true);
    });

    it('sem decisão de acesso provida, NEGA', async () => {
      TestBed.resetTestingModule();
      await TestBed.configureTestingModule({
        imports: [OnboardingWizardPageComponent],
        providers: [
          provideRouter([]),
          { provide: AcsTenantFacade, useValue: tenantFacade },
            { provide: AcsShellIntegrationService, useValue: acsIntegration },
            { provide: PacShellIntegrationService, useValue: pacIntegration },
          { provide: EdgeShellIntegrationService, useValue: edgeIntegration },
          { provide: ACS_ACCESS_DECISION, useValue: null },
        ],
      }).compileComponents();
      const f = TestBed.createComponent(OnboardingWizardPageComponent);
      f.detectChanges();
      expect(f.componentInstance.podeCriarEmpresa()).toBe(false);
    });
  });
});
