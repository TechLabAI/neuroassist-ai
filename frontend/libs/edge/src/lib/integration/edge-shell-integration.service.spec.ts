import { TestBed } from '@angular/core/testing';
import { EdgeShellIntegrationService } from './edge-shell-integration.service';
import { DevicePairingPageComponent } from '../feature-pairing/device-pairing-page.component';
import { DEVICE_PORT } from '../api/device.port';
import { PAIRING_PORT } from '../api/pairing.port';
import { of } from 'rxjs';

// =============================================================================
// EdgeShellIntegrationService — o EDGE passou a DECLARAR o seu passo.
//
// Antes o assistente de configuração inicial é que sabia disto: tinha um bloco
// de template com o número 3, o título "Dispositivo edge" e as entradas do
// pareamento escritas à mão, e só pedia o componente ao EDGE. Ou seja, o passo
// era do shell; só o componente era do módulo.
// =============================================================================

const portaDispositivoVazia = {
  resolveForPaciente: () => of(null),
  liveness: () => of(null),
  get: () => of(null),
  list: () => of([]),
  register: () => of(null),
  revoke: () => of(null),
};

const portaPareamentoVazia = {
  requestCode: () => of(null),
  poll: () => of(null),
  cancel: () => of(null),
};

describe('EdgeShellIntegrationService — composição do assistente', () => {
  let service: EdgeShellIntegrationService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        { provide: DEVICE_PORT, useValue: portaDispositivoVazia },
        { provide: PAIRING_PORT, useValue: portaPareamentoVazia },
      ],
    });
    service = TestBed.inject(EdgeShellIntegrationService);
  });

  it('declara UM passo, o do dispositivo, com a superfície de pareamento real', async () => {
    const passos = service.onboardingWizardComposition();
    expect(passos).toHaveLength(1);
    expect(passos[0].key).toBe('edge-device-pairing');
    expect(passos[0].title).toBe('Dispositivo edge');
    await expect(passos[0].component).resolves.toBe(DevicePairingPageComponent);
  });

  it('as entradas do passo saem do CONTEXTO — o EDGE não descobre o paciente sozinho', () => {
    const passo = service.onboardingWizardComposition()[0];
    expect(passo.inputs?.({ pacienteId: 'pac-marcio' })).toEqual({
      pacienteHint: 'pac-marcio',
      composto: true,
    });
  });

  // CONTROLE NEGATIVO das entradas: sem paciente no contexto o que chega é
  // `null`, e não um valor lembrado da chamada anterior. Um `inputs` que fosse
  // objeto congelado na declaração — em vez de função do contexto — passaria no
  // teste acima e reprovaria aqui.
  it('sem paciente no contexto, a dica de paciente é null', () => {
    const passo = service.onboardingWizardComposition()[0];
    expect(passo.inputs?.({ pacienteId: 'pac-marcio' })['pacienteHint']).toBe('pac-marcio');
    expect(passo.inputs?.({ pacienteId: null })['pacienteHint']).toBeNull();
  });

  // CONTROLE NEGATIVO de `composto`: sem esta entrada o pareamento traz o
  // próprio <h1> e o próprio contêiner, e o assistente fica com dois títulos de
  // nível 1. É a entrada que faz dele um PEDAÇO da página.
  it('o passo é declarado como COMPOSTO, não como página', () => {
    const passo = service.onboardingWizardComposition()[0];
    expect(passo.inputs?.({ pacienteId: null })['composto']).toBe(true);
  });

  it('resolvePairingSlot() continua entregando a superfície de pareamento', async () => {
    await expect(service.resolvePairingSlot()).resolves.toBe(DevicePairingPageComponent);
  });
});
