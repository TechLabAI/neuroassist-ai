import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { DeviceSessionStore, AVISO_CREDENCIAL_DISPOSITIVO } from './device-session.store';
import { DeviceAuthHttpAdapter, DeviceAuthErro } from './device-auth-http.adapter';
import type { DeviceAuthResult } from './device-auth.port';

// =============================================================================
// DeviceSessionStore — unit tests (P2 — GEST-FE-027)
//
// O que estes testes protegem:
//   1. Máquina de estados: desconhecido → autenticando → autenticado | credencial-invalida
//   2. 401 → credencial-invalida TERMINAL (sem laço de retry automático)
//   3. deviceId vem de sub (nunca de config local)
//   4. tenantId vem de tenant (nunca de config local)
//   5. clientSecret nunca entra em nenhum signal
//   6. Reauth proativa agendada a 80% do expiresIn
//   7. 401 na reauth → credencial-invalida (terminal)
//   8. Aviso de segurança ADR-008 P1 presente e não vazio
// =============================================================================

const RESULTADO_SUCESSO: DeviceAuthResult = {
  accessToken: 'eyJhbGciOiJSUzI1NiJ9.fixture-device.assinatura',
  refreshToken: null,
  tokenType: 'Bearer',
  expiresIn: 3600,
  deviceId: 'a0000000-0000-0000-0000-000000000042',
  tenantId: 'd0000000-0000-0000-0000-000000000001',
  authorities: ['edge.device'],
};

/** Cria um adapter fake para testes — evita qualquer chamada HTTP. */
function criarAdapterFake(comportamento: 'sucesso' | 'credencial-invalida' | 'rede' | 'servidor') {
  const autenticarFn = jest.fn<Promise<DeviceAuthResult>, [{ clientId: string; clientSecret: string }]>();
  switch (comportamento) {
    case 'sucesso':
      autenticarFn.mockResolvedValue(RESULTADO_SUCESSO);
      break;
    case 'credencial-invalida':
      autenticarFn.mockRejectedValue(new DeviceAuthErro('credencial-invalida'));
      break;
    case 'rede':
      autenticarFn.mockRejectedValue(new DeviceAuthErro('rede'));
      break;
    case 'servidor':
      autenticarFn.mockRejectedValue(new DeviceAuthErro('servidor'));
      break;
  }
  return { autenticar: autenticarFn } as unknown as DeviceAuthHttpAdapter;
}

function setupStore(adapter: DeviceAuthHttpAdapter): DeviceSessionStore {
  TestBed.configureTestingModule({
    providers: [
      DeviceSessionStore,
      { provide: DeviceAuthHttpAdapter, useValue: adapter },
    ],
  });
  return TestBed.inject(DeviceSessionStore);
}

describe('DeviceSessionStore', () => {
  afterEach(() => {
    TestBed.resetTestingModule();
    jest.clearAllTimers();
  });

  describe('estado inicial', () => {
    it('deve iniciar em estado desconhecido', () => {
      const store = setupStore(criarAdapterFake('sucesso'));
      expect(store.estado()).toBe('desconhecido');
    });

    it('deve ter token, deviceId e tenantId nulos no estado inicial', () => {
      const store = setupStore(criarAdapterFake('sucesso'));
      expect(store.token()).toBeNull();
      expect(store.deviceId()).toBeNull();
      expect(store.tenantId()).toBeNull();
    });

    it('deve ter autenticado = false no estado inicial', () => {
      const store = setupStore(criarAdapterFake('sucesso'));
      expect(store.autenticado()).toBe(false);
    });
  });

  describe('autenticação bem-sucedida', () => {
    it('deve transitar para autenticado após sucesso', async () => {
      const store = setupStore(criarAdapterFake('sucesso'));
      await store.autenticar('edge-abc', 'secret-123');
      expect(store.estado()).toBe('autenticado');
    });

    it('deve preencher deviceId com o sub da resposta', async () => {
      const store = setupStore(criarAdapterFake('sucesso'));
      await store.autenticar('edge-abc', 'secret-123');
      expect(store.deviceId()).toBe('a0000000-0000-0000-0000-000000000042');
    });

    it('deve preencher tenantId com o tenant da resposta', async () => {
      const store = setupStore(criarAdapterFake('sucesso'));
      await store.autenticar('edge-abc', 'secret-123');
      expect(store.tenantId()).toBe('d0000000-0000-0000-0000-000000000001');
    });

    it('deve preencher token com o accessToken da resposta', async () => {
      const store = setupStore(criarAdapterFake('sucesso'));
      await store.autenticar('edge-abc', 'secret-123');
      expect(store.token()).toBe(RESULTADO_SUCESSO.accessToken);
    });

    it('deve marcar autenticado = true após sucesso', async () => {
      const store = setupStore(criarAdapterFake('sucesso'));
      await store.autenticar('edge-abc', 'secret-123');
      expect(store.autenticado()).toBe(true);
    });

    it('não deve expor clientSecret em nenhum signal', async () => {
      const store = setupStore(criarAdapterFake('sucesso'));
      await store.autenticar('edge-abc', 'segredo-nao-pode-vazar');

      // Verificar cada signal público: nenhum deve conter o segredo
      const stateValues = [
        JSON.stringify(store.token()),
        JSON.stringify(store.deviceId()),
        JSON.stringify(store.tenantId()),
        store.estado(),
      ];
      for (const val of stateValues) {
        if (val) expect(val).not.toContain('segredo-nao-pode-vazar');
      }
    });
  });

  describe('autenticação com 401 — estado terminal', () => {
    it('deve transitar para credencial-invalida em caso de 401', async () => {
      const store = setupStore(criarAdapterFake('credencial-invalida'));
      await store.autenticar('edge-abc', 'errado');
      expect(store.estado()).toBe('credencial-invalida');
    });

    it('credencial-invalida — segunda tentativa manual pode recuperar o estado', async () => {
      // O store em si não bloqueia nova chamada manual — o terminal significa que a reauth
      // automática para. Uma tentativa manual nova pode mudar o estado.
      const autenticarFn = jest.fn<Promise<DeviceAuthResult>, [{ clientId: string; clientSecret: string }]>();
      // Primeira chamada: 401
      autenticarFn.mockRejectedValueOnce(new DeviceAuthErro('credencial-invalida'));
      const adapterFake = { autenticar: autenticarFn } as unknown as DeviceAuthHttpAdapter;

      const store = setupStore(adapterFake);
      await store.autenticar('edge-abc', 'errado');
      expect(store.estado()).toBe('credencial-invalida');

      // Agora corrigindo — segunda tentativa bem-sucedida
      autenticarFn.mockResolvedValueOnce(RESULTADO_SUCESSO);
      await store.autenticar('edge-abc', 'correto');
      expect(store.estado()).toBe('autenticado');
    });

    it('deve limpar token e deviceId ao entrar em credencial-invalida', async () => {
      const autenticarFn = jest.fn<Promise<DeviceAuthResult>, [{ clientId: string; clientSecret: string }]>();

      // Primeiro autentica com sucesso
      autenticarFn.mockResolvedValueOnce(RESULTADO_SUCESSO);
      const adapterFake = { autenticar: autenticarFn } as unknown as DeviceAuthHttpAdapter;
      const store = setupStore(adapterFake);
      await store.autenticar('edge-abc', 'correto');
      expect(store.token()).not.toBeNull();

      // Depois reautentica com credencial inválida
      autenticarFn.mockRejectedValueOnce(new DeviceAuthErro('credencial-invalida'));
      await store.autenticar('edge-abc', 'errado');
      expect(store.token()).toBeNull();
      expect(store.deviceId()).toBeNull();
      expect(store.tenantId()).toBeNull();
    });

    it('autenticar não propaga exceção para o chamador — erro fica no estado', async () => {
      const store = setupStore(criarAdapterFake('credencial-invalida'));
      // autenticar absorve o erro — não o propaga para fora
      await expect(store.autenticar('edge-abc', 'segredo-123')).resolves.toBeUndefined();
      expect(store.estado()).toBe('credencial-invalida');
    });
  });

  describe('falha de rede ou servidor', () => {
    it('falha de rede deve transitar para desconhecido (não terminal)', async () => {
      const store = setupStore(criarAdapterFake('rede'));
      await store.autenticar('edge-abc', 'secret-123');
      expect(store.estado()).toBe('desconhecido');
    });

    it('falha de servidor deve transitar para desconhecido (não terminal)', async () => {
      const store = setupStore(criarAdapterFake('servidor'));
      await store.autenticar('edge-abc', 'secret-123');
      expect(store.estado()).toBe('desconhecido');
    });
  });

  describe('reautenticação proativa', () => {
    beforeEach(() => jest.useFakeTimers());
    afterEach(() => jest.useRealTimers());

    it('deve agendar reauth a 80% do expiresIn', async () => {
      const autenticarFn = jest.fn<Promise<DeviceAuthResult>, [{ clientId: string; clientSecret: string }]>();
      autenticarFn.mockResolvedValue(RESULTADO_SUCESSO);
      const adapterFake = { autenticar: autenticarFn } as unknown as DeviceAuthHttpAdapter;

      const store = setupStore(adapterFake);
      await store.autenticar('edge-abc', 'secret-123');

      // expiresIn = 3600s → 80% = 2880s = 2_880_000ms
      // Avançar 79% — ainda não deve ter reautenticado
      jest.advanceTimersByTime(2_879_000);
      expect(autenticarFn).toHaveBeenCalledTimes(1);

      // Avançar além de 80% — reauth deve ter sido disparada
      jest.advanceTimersByTime(2_000);
      // Resolver microtasks pendentes
      await Promise.resolve();

      // A segunda chamada acontece na reauth proativa
      expect(autenticarFn.mock.calls.length).toBeGreaterThanOrEqual(2);
    });

    it('401 na reauth proativa deve levar a credencial-invalida (terminal)', async () => {
      const autenticarFn = jest.fn<Promise<DeviceAuthResult>, [{ clientId: string; clientSecret: string }]>();
      // Primeira autenticação bem-sucedida
      autenticarFn.mockResolvedValueOnce(RESULTADO_SUCESSO);

      const adapterFake = { autenticar: autenticarFn } as unknown as DeviceAuthHttpAdapter;
      const store = setupStore(adapterFake);
      await store.autenticar('edge-abc', 'secret-123');
      expect(store.estado()).toBe('autenticado');

      // Simular que credencial expirou/foi revogada → reauth retorna 401
      autenticarFn.mockRejectedValue(new DeviceAuthErro('credencial-invalida'));

      // Avançar para o momento da reauth proativa
      jest.advanceTimersByTime(2_880_000);
      await Promise.resolve();
      await Promise.resolve(); // Flush promise chain

      expect(store.estado()).toBe('credencial-invalida');
    });
  });

  describe('aviso de segurança ADR-008 P1', () => {
    it('deve expor aviso de segurança não vazio', () => {
      const store = setupStore(criarAdapterFake('sucesso'));
      expect(store.avisoSeguranca).toBeTruthy();
      expect(store.avisoSeguranca.length).toBeGreaterThan(50);
    });

    it('aviso deve mencionar piloto fechado / máquina dedicada', () => {
      const store = setupStore(criarAdapterFake('sucesso'));
      const aviso = store.avisoSeguranca.toLowerCase();
      expect(aviso).toContain('piloto fechado');
    });

    it('aviso deve mencionar clientSecret', () => {
      expect(AVISO_CREDENCIAL_DISPOSITIVO).toContain('clientSecret');
    });

    it('AVISO_CREDENCIAL_DISPOSITIVO deve ser a constante exportada', () => {
      const store = setupStore(criarAdapterFake('sucesso'));
      expect(store.avisoSeguranca).toBe(AVISO_CREDENCIAL_DISPOSITIVO);
    });
  });
});
