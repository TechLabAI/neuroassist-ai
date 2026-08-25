import { firstValueFrom, Observable } from 'rxjs';
import { AcsTenantMockAdapter } from './acs-tenant-mock.adapter';
import { ACS_E2E_FLAGS } from './acs-e2e-flags';

// =============================================================================
// AcsTenantMockAdapter — as duas bandeiras de provisionamento.
//
// O adaptador devolvia 'active' incondicionalmente nos três métodos. O
// acs-service provisiona schema por SAGA e responde 202: o estado intermediário
// é real, `AcsTenantFacade.provisioning()` já o representava, e nenhum teste
// podia alcançá-lo — a tela do provisionamento não tinha como ser exercitada.
//
// Cada afirmação abaixo vem com o seu CONTROLE NEGATIVO (o mesmo método sem a
// bandeira), porque uma bandeira que não faz efeito e uma bandeira que faz
// efeito sempre produzem o mesmo verde quando só o caso ligado é medido.
// =============================================================================

function ligar(bandeira: string): void {
  localStorage.setItem(bandeira, 'true');
}

describe('AcsTenantMockAdapter — bandeiras de provisionamento', () => {
  let adapter: AcsTenantMockAdapter;

  beforeEach(() => {
    localStorage.clear();
    adapter = new AcsTenantMockAdapter();
    jest.useFakeTimers();
  });

  afterEach(() => {
    jest.useRealTimers();
    localStorage.clear();
  });

  /** Resolve a Observable do mock adiantando os temporizadores. */
  async function correr<T>(obs: Observable<T>): Promise<T> {
    const p = firstValueFrom(obs);
    await jest.advanceTimersByTimeAsync(1000);
    return p;
  }

  describe('create()', () => {
    it("sem bandeira devolve 'active' (201 — controle negativo)", async () => {
      const dto = await correr(adapter.create({ nome: 'Clínica X', tipo: 'clinica' }));
      expect(dto.provisioningStatus).toBe('active');
    });

    it("com empresaProvisionando devolve 'provisioning' (202)", async () => {
      ligar(ACS_E2E_FLAGS.empresaProvisionando);
      const dto = await correr(adapter.create({ nome: 'Clínica X', tipo: 'clinica' }));
      expect(dto.provisioningStatus).toBe('provisioning');
    });
  });

  describe('get()', () => {
    it("sem bandeira o registro semeado vem 'active' (controle negativo)", async () => {
      const dto = await correr(adapter.get('tenant-familia-amaral'));
      expect(dto.provisioningStatus).toBe('active');
    });

    it('com empresaProvisionando o registro semeado vem em provisionamento', async () => {
      ligar(ACS_E2E_FLAGS.empresaProvisionando);
      const dto = await correr(adapter.get('tenant-familia-amaral'));
      expect(dto.provisioningStatus).toBe('provisioning');
    });
  });

  describe('retryProvisioning()', () => {
    it("sem bandeira a retentativa RESOLVE para 'active' (controle negativo)", async () => {
      ligar(ACS_E2E_FLAGS.empresaProvisionando);
      const criado = await correr(adapter.create({ nome: 'Retry Corp', tipo: 'familia' }));
      localStorage.removeItem(ACS_E2E_FLAGS.empresaProvisionando);
      const depois = await correr(adapter.retryProvisioning(criado.id));
      expect(depois.provisioningStatus).toBe('active');
    });

    it('com retryNaoResolve a retentativa continua em provisionamento', async () => {
      ligar(ACS_E2E_FLAGS.empresaProvisionando);
      ligar(ACS_E2E_FLAGS.retryNaoResolve);
      const criado = await correr(adapter.create({ nome: 'Retry Corp', tipo: 'familia' }));
      const depois = await correr(adapter.retryProvisioning(criado.id));
      expect(depois.provisioningStatus).toBe('provisioning');
    });

    // O tipo do frontend admite 'failed'; o backend só emite
    // PROVISIONING|ACTIVE|SUSPENDED. O mock NÃO ensina a tela a esperar um
    // estado sem produtor — divergência relatada, não contornada aqui.
    it("nunca devolve 'failed' — o backend não emite esse estado", async () => {
      ligar(ACS_E2E_FLAGS.empresaProvisionando);
      ligar(ACS_E2E_FLAGS.retryNaoResolve);
      const criado = await correr(adapter.create({ nome: 'Retry Corp', tipo: 'familia' }));
      const depois = await correr(adapter.retryProvisioning(criado.id));
      expect(depois.provisioningStatus).not.toBe('failed');
    });
  });

  it('as duas bandeiras têm literais distintos e não colidem com as antigas', () => {
    const valores = Object.values(ACS_E2E_FLAGS);
    expect(new Set(valores).size).toBe(valores.length);
  });
});
