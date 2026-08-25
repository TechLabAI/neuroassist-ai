import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { PacShellIntegrationService } from './pac-shell-integration.service';
import { PatientRegisterComponent } from '../feature-patient-register/patient-register.component';
import { FirstCommandStepComponent } from '../feature-first-command/first-command-step.component';
import { PAC_PACIENTE_PORT } from '../api/paciente.port';
import { PacPacienteFacade } from '../pac-paciente.facade';

// =============================================================================
// PacShellIntegrationService — o PAC passou a DECLARAR os seus passos.
//
// Era o acoplamento mais direto que sobrava, em dois lugares:
//   - o assistente importava `PatientRegisterComponent` do barril do PAC e o
//     montava com `<pac-patient-register />`, num bloco com o número e o título
//     escritos à mão;
//   - "Primeiro comando" era um `<a routerLink="/pac">` — o passo não fazia a
//     coisa, mandava a pessoa embora.
// =============================================================================

const portaVazia = {
  list: () => of({ items: [], total: 0, page: 0, size: 20 }),
  get: () => of(null),
  create: () => of(null),
  update: () => of(null),
};

describe('PacShellIntegrationService — composição do assistente', () => {
  let service: PacShellIntegrationService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      // `PacPacienteFacade` não é `providedIn: 'root'` — quem a fornece é
      // `providePac()`. Aqui ela entra explicitamente, com a porta dublê.
      providers: [PacPacienteFacade, { provide: PAC_PACIENTE_PORT, useValue: portaVazia }],
    });
    service = TestBed.inject(PacShellIntegrationService);
  });

  it('resolvePatientRegisterSlot() entrega a superfície de cadastro real', async () => {
    await expect(service.resolvePatientRegisterSlot()).resolves.toBe(PatientRegisterComponent);
  });

  it('resolveFirstCommandSlot() entrega a superfície do primeiro comando', async () => {
    await expect(service.resolveFirstCommandSlot()).resolves.toBe(FirstCommandStepComponent);
  });

  it('declara DOIS passos: o do paciente e o do primeiro comando', async () => {
    const passos = service.onboardingWizardComposition();
    expect(passos.map((p) => p.key)).toEqual(['pac-patient-register', 'pac-first-command']);
    expect(passos.map((p) => p.title)).toEqual(['Paciente', 'Primeiro comando']);
    await expect(passos[0].component).resolves.toBe(PatientRegisterComponent);
    await expect(passos[1].component).resolves.toBe(FirstCommandStepComponent);
  });

  // CONTROLE NEGATIVO de `composto`: sem esta entrada o cadastro traz o próprio
  // cartão e o próprio <h2> "Paciente", e o assistente — que já desenha o número
  // e o título — fica com dois cabeçalhos iguais, um dentro do outro.
  it('o passo do paciente é declarado como COMPOSTO, não como página', () => {
    const passo = service.onboardingWizardComposition()[0];
    expect(passo.inputs?.({ pacienteId: null })).toEqual({ composto: true });
  });

  // CONTROLE NEGATIVO do contexto, nos dois sentidos: o passo do PACIENTE é
  // justamente o que não depende do paciente ativo (é ele que o cria), e o do
  // PRIMEIRO COMANDO depende — um comando pertence a alguém.
  it('o passo do paciente ignora o contexto; o do primeiro comando o consome', () => {
    const [paciente, comando] = service.onboardingWizardComposition();
    expect(paciente.inputs?.({ pacienteId: 'pac-existente' })).toEqual({ composto: true });
    expect(comando.inputs?.({ pacienteId: 'pac-marcio' })).toEqual({ pacienteHint: 'pac-marcio' });
    expect(comando.inputs?.({ pacienteId: null })).toEqual({ pacienteHint: null });
  });

  // A ordem é uma decisão de PRODUTO distribuída entre módulos, e nenhum deles
  // conhece o outro para se posicionar por vizinhança — daí números:
  // paciente < dispositivo (3) < primeiro comando < convite (5).
  it('a ordem declarada põe o paciente antes do dispositivo e o comando depois', () => {
    const [paciente, comando] = service.onboardingWizardComposition();
    expect(paciente.order).toBeLessThan(3);
    expect(comando.order).toBeGreaterThan(3);
    expect(comando.order).toBeLessThan(5);
  });

  it('pacienteAtivoId é null enquanto não houver paciente — é o contexto do assistente', () => {
    expect(service.pacienteAtivoId()).toBeNull();
    expect(service.temPaciente()).toBe(false);
  });
});
