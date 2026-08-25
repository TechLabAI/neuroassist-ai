import { Component, CUSTOM_ELEMENTS_SCHEMA, Input, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import {
  AcsTenantFacade,
  AcsCaregiverFacade,
  AcsShellIntegrationService,
  ACS_ACCESS_DECISION,
  ACS_MODULE_DESCRIPTOR,
} from '@neuroassist/acs';
import type { TenantVm } from '@neuroassist/acs';
import { EdgeShellIntegrationService } from '@neuroassist/edge';
import { PacPacienteFacade, PatientRegisterComponent } from '@neuroassist/pac';
import { OnboardingWizardPageComponent } from './onboarding-wizard-page.component';

// =============================================================================
// OnboardingWizardPageComponent — os três estados da empresa, a permissão de
// provisionar e o passo de vínculo.
//
// O QUE ESTES TESTES SEGURAM
//
//   1. Uma empresa que voltou 202 NÃO é uma empresa pronta. A tela usava
//      `!!tenant()` em três lugares (insígnia, checklist, podeConcluir) e por
//      isso mostrava check verde para uma empresa cujo schema ainda estava
//      sendo provisionado.
//   2. Criar empresa exige `acs.tenant.provisionar`, que nenhum papel humano
//      possui (RoleAuthorityPolicy, E2E-029). O botão fica desabilitado até
//      para a coordenadora.
//   3. O passo de vínculo existe e é composto por slot — não é um <div> com
//      testid.
//
// Cada afirmação tem o seu CONTROLE NEGATIVO ao lado: o estado em que a
// asserção PRECISA cair. Sem ele, um `[disabled]` que nunca habilita e um
// `[disabled]` que nunca desabilita produzem o mesmo verde.
// =============================================================================

// --- dublês de composição --------------------------------------------------
// Precisam declarar as MESMAS entradas que a página passa por
// `ngComponentOutletInputs`: o outlet chama `setInput()`, e entrada não
// declarada estoura NG0303 — o que, aliás, é a prova de que a página está
// passando exatamente estas.

@Component({ selector: 'stub-pareamento', standalone: true, template: '<p>pareamento</p>' })
class StubPareamentoComponent {
  @Input() pacienteHint: string | null = null;
  @Input() composto = false;
}

@Component({ selector: 'stub-vinculo', standalone: true, template: '<p data-testid="stub-vinculo">vínculo</p>' })
class StubVinculoComponent {
  @Input() pacienteId: string | null = null;
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

describe('OnboardingWizardPageComponent', () => {
  let fixture: ComponentFixture<OnboardingWizardPageComponent>;
  let comp: OnboardingWizardPageComponent;

  const tenantSignal = signal<TenantVm | null>(null);
  const loadingSignal = signal(false);
  const errorSignal = signal<string | null>(null);
  const provisioningSignal = signal(false);
  const pacienteSignal = signal<{ id: string } | null>(null);
  const hasPatientSignal = signal(false);

  const tenantFacade = {
    tenant: tenantSignal.asReadonly(),
    loading: loadingSignal.asReadonly(),
    error: errorSignal.asReadonly(),
    provisioning: provisioningSignal.asReadonly(),
    create: jest.fn().mockResolvedValue(EMPRESA_ATIVA),
    retryProvisioning: jest.fn().mockResolvedValue(undefined),
    load: jest.fn().mockResolvedValue(undefined),
  };

  const caregiverFacade = {
    error: signal<string | null>(null).asReadonly(),
    invite: jest.fn().mockResolvedValue(undefined),
  };

  const pacFacade = {
    patient: pacienteSignal.asReadonly(),
    hasPatient: hasPatientSignal.asReadonly(),
  };

  const edgeIntegration = {
    connectivityReadModel: () => signal({ paired: false }).asReadonly(),
    pairingPhase: () => signal('idle').asReadonly(),
    resolvePairingSlot: jest.fn().mockResolvedValue(StubPareamentoComponent),
  };

  const acsIntegration = {
    resolveCaregiverLinkSlot: jest.fn().mockResolvedValue(StubVinculoComponent),
  };

  const decisao = { can: jest.fn().mockReturnValue(false) };

  async function montar(): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [OnboardingWizardPageComponent],
      providers: [
        provideRouter([]),
        { provide: AcsTenantFacade, useValue: tenantFacade },
        { provide: AcsCaregiverFacade, useValue: caregiverFacade },
        { provide: AcsShellIntegrationService, useValue: acsIntegration },
        { provide: PacPacienteFacade, useValue: pacFacade },
        { provide: EdgeShellIntegrationService, useValue: edgeIntegration },
        { provide: ACS_ACCESS_DECISION, useValue: decisao },
      ],
    })
      // O passo 2 monta o PatientRegisterComponent do PAC de verdade; aqui ele
      // arrastaria meio módulo PAC para um teste que não é sobre ele.
      .overrideComponent(OnboardingWizardPageComponent, {
        remove: { imports: [PatientRegisterComponent] },
        add: { schemas: [CUSTOM_ELEMENTS_SCHEMA] },
      })
      .compileComponents();

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
    provisioningSignal.set(false);
    pacienteSignal.set(null);
    hasPatientSignal.set(false);
    decisao.can.mockReturnValue(false);
    jest.clearAllMocks();
    acsIntegration.resolveCaregiverLinkSlot.mockResolvedValue(StubVinculoComponent);
    edgeIntegration.resolvePairingSlot.mockResolvedValue(StubPareamentoComponent);
    tenantFacade.create.mockResolvedValue(EMPRESA_ATIVA);
    tenantFacade.retryProvisioning.mockResolvedValue(undefined);
  });

  // ---------------------------------------------------------------------------
  // 1. Os três estados da empresa
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
    // a tela olha `isActive`, e não a mera existência do registro — trocar de
    // volta para `!!tenant()` reprova este teste E o anterior.
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
  // 2. Checklist e conclusão — o mesmo defeito, repetido
  // ---------------------------------------------------------------------------
  describe('checklist e conclusão', () => {
    it('empresa em provisionamento NÃO marca "Unidade de cuidado" como pronto', async () => {
      tenantSignal.set(EMPRESA_PROVISIONANDO);
      await montar();
      expect(comp.checklist().find((i) => i.label === 'Unidade de cuidado')?.done).toBe(false);
    });

    it('empresa ativa MARCA "Unidade de cuidado" como pronto (controle negativo)', async () => {
      tenantSignal.set(EMPRESA_ATIVA);
      await montar();
      expect(comp.checklist().find((i) => i.label === 'Unidade de cuidado')?.done).toBe(true);
    });

    it('podeConcluir() é falso com empresa em provisionamento, mesmo com o resto pronto', async () => {
      tenantSignal.set(EMPRESA_PROVISIONANDO);
      await montar();
      comp.onDevicePaired(null);
      comp.cuidadoraEmail.set('a@b.com');
      fixture.detectChanges();
      expect(comp.podeConcluir()).toBe(false);
    });

    it('podeConcluir() é verdadeiro com empresa ATIVA e o resto pronto (controle negativo)', async () => {
      tenantSignal.set(EMPRESA_ATIVA);
      await montar();
      comp.onDevicePaired(null);
      comp.cuidadoraEmail.set('a@b.com');
      fixture.detectChanges();
      expect(comp.podeConcluir()).toBe(true);
    });
  });

  // ---------------------------------------------------------------------------
  // 3. Retentativa
  // ---------------------------------------------------------------------------
  describe('retentativa de provisionamento', () => {
    it('oferece "Tentar novamente" quando a empresa não está ativa', async () => {
      tenantSignal.set(EMPRESA_PROVISIONANDO);
      await montar();
      const btn = achar('tenant-retry');
      expect(btn?.textContent).toMatch(/tentar novamente/i);
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

      // Antes de tentar: espera normal da saga, sem aviso de falha.
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
  // 4. Permissão de provisionar
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

    // CONTROLE NEGATIVO: com a authority, e só com ela, o botão habilita. Sem
    // este par, um botão permanentemente desabilitado (por qualquer outro
    // motivo) passaria pelo teste acima.
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
      await TestBed.resetTestingModule();
      await TestBed.configureTestingModule({
        imports: [OnboardingWizardPageComponent],
        providers: [
          provideRouter([]),
          { provide: AcsTenantFacade, useValue: tenantFacade },
          { provide: AcsCaregiverFacade, useValue: caregiverFacade },
          { provide: AcsShellIntegrationService, useValue: acsIntegration },
          { provide: PacPacienteFacade, useValue: pacFacade },
          { provide: EdgeShellIntegrationService, useValue: edgeIntegration },
          { provide: ACS_ACCESS_DECISION, useValue: null },
        ],
      })
        .overrideComponent(OnboardingWizardPageComponent, {
          remove: { imports: [PatientRegisterComponent] },
          add: { schemas: [CUSTOM_ELEMENTS_SCHEMA] },
        })
        .compileComponents();
      const f = TestBed.createComponent(OnboardingWizardPageComponent);
      f.detectChanges();
      expect(f.componentInstance.podeCriarEmpresa()).toBe(false);
    });
  });

  // ---------------------------------------------------------------------------
  // 5. Passo de vínculo
  // ---------------------------------------------------------------------------
  describe('passo de vínculo', () => {
    it('monta o passo pelo slot do ACS — não é um <div> com testid', async () => {
      await montar();
      expect(acsIntegration.resolveCaregiverLinkSlot).toHaveBeenCalledTimes(1);
      expect(achar('caregiver-link-step')).not.toBeNull();
      // O conteúdo do passo é o componente entregue pelo slot, montado de fato.
      expect(achar('stub-vinculo')).not.toBeNull();
    });

    // CONTROLE NEGATIVO: sem componente resolvido, o passo mostra carregamento e
    // NADA do conteúdo. Prova que o testid não está pendurado num invólucro que
    // ficaria verde de qualquer jeito.
    it('enquanto o slot não resolve, o passo não tem conteúdo de vínculo', async () => {
      acsIntegration.resolveCaregiverLinkSlot.mockReturnValue(new Promise(() => undefined));
      await montar();
      expect(achar('stub-vinculo')).toBeNull();
      expect(texto()).toMatch(/carregando módulo de vínculo/i);
    });

    it('passa o paciente ativo e composto=true ao componente do slot', async () => {
      pacienteSignal.set({ id: 'pac-marcio' });
      await montar();
      expect(comp.caregiverLinkInputs()).toEqual({ pacienteId: 'pac-marcio', composto: true });
    });

    it('sem paciente ativo, passa null — a superfície decide o que exibir', async () => {
      await montar();
      expect(comp.caregiverLinkInputs()['pacienteId']).toBeNull();
    });
  });
});
