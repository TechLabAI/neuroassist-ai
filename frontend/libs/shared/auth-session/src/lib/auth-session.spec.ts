import { TestBed } from '@angular/core/testing';
import { AuthSessionStore, type TokenResponse } from './auth-session';

// =============================================================================
// As decisões deste store têm consequência de segurança e de ergonomia clínica.
// Cada uma tem um teste que fica vermelho se alguém a desfizer.
// =============================================================================

const AGORA = 1_700_000_000_000;

function resposta(over: Partial<TokenResponse> = {}): TokenResponse {
  // Objeto literal com campos nomeados, e não posicional: `AutenticacaoResponse` é
  // um record Java, e acrescentar campo lá quebra quem constrói por posição — foi
  // o que aconteceu com quatro testes do backend ao acrescentar `nome`.
  return {
    accessToken: 'token-abc',
    refreshToken: null,
    tokenType: 'Bearer',
    expiresIn: 900,
    tenant: 'e2e00000-0000-0000-0000-000000000001',
    sub: 'e2e00000-0000-0000-0000-000000000010',
    papel: 'COORDENADOR_CLINICA',
    nome: 'Coord Dev',
    authorities: ['acs.cuidador.criar', 'pac.paciente.vincular'],
    ...over,
  };
}

describe('AuthSessionStore', () => {
  let store: AuthSessionStore;

  beforeEach(() => {
    jest.spyOn(Date, 'now').mockReturnValue(AGORA);
    sessionStorage.clear();
    TestBed.configureTestingModule({});
    store = TestBed.inject(AuthSessionStore);
  });

  afterEach(() => jest.restoreAllMocks());

  it('começa deslogado', () => {
    expect(store.autenticado()).toBe(false);
    expect(store.user()).toBeNull();
    expect(store.token()).toBeNull();
  });

  it('entrar popula a sessão a partir do corpo da resposta', () => {
    store.entrar(resposta());

    expect(store.autenticado()).toBe(true);
    expect(store.token()).toBe('token-abc');
    expect(store.user()?.nome).toBe('Coord Dev');
    expect(store.user()?.papel).toBe('COORDENADOR_CLINICA');
    expect(store.user()?.tenantId).toBe('e2e00000-0000-0000-0000-000000000001');
  });

  it('expiraEm é calculado a partir de expiresIn', () => {
    store.entrar(resposta({ expiresIn: 900 }));
    expect(store.user()?.expiraEm.getTime()).toBe(AGORA + 900_000);
  });

  // ── a decisão que protege o computador compartilhado ────────────────────────

  it('persiste em sessionStorage, não em localStorage', () => {
    // Num contexto clínico o mesmo computador é usado por mais de uma pessoa. Um
    // token em localStorage sobrevive ao fechamento da aba — e a próxima pessoa
    // que sentar ali herda a sessão.
    store.entrar(resposta());

    expect(sessionStorage.getItem('neuroassist.session')).not.toBeNull();
    expect(localStorage.getItem('neuroassist.session')).toBeNull();
  });

  it('sair limpa memória e armazenamento', () => {
    store.entrar(resposta());
    store.sair();

    expect(store.autenticado()).toBe(false);
    expect(store.token()).toBeNull();
    expect(sessionStorage.getItem('neuroassist.session')).toBeNull();
  });

  it('sair é idempotente', () => {
    expect(() => {
      store.sair();
      store.sair();
    }).not.toThrow();
  });

  // ── token vencido ───────────────────────────────────────────────────────────

  it('token vencido não conta como autenticado', () => {
    store.entrar(resposta({ expiresIn: 900 }));
    jest.spyOn(Date, 'now').mockReturnValue(AGORA + 901_000);

    expect(store.autenticado()).toBe(false);
  });

  it('sessão vencida no armazenamento não é restaurada', () => {
    // Restaurar uma sessão vencida faria a tela desenhar o menu de alguém que vai
    // levar 401 na primeira chamada — pior que mostrar a tela de login.
    store.entrar(resposta({ expiresIn: 900 }));
    jest.spyOn(Date, 'now').mockReturnValue(AGORA + 901_000);

    const novo = new AuthSessionStore();

    expect(novo.autenticado()).toBe(false);
    expect(sessionStorage.getItem('neuroassist.session')).toBeNull();
  });

  it('sessão válida no armazenamento é restaurada ao recarregar', () => {
    store.entrar(resposta());

    const novo = new AuthSessionStore();

    expect(novo.autenticado()).toBe(true);
    expect(novo.user()?.nome).toBe('Coord Dev');
  });

  it('armazenamento corrompido começa deslogado em vez de estourar', () => {
    sessionStorage.setItem('neuroassist.session', 'isto não é json');

    expect(() => new AuthSessionStore()).not.toThrow();
    expect(new AuthSessionStore().autenticado()).toBe(false);
  });

  // ── authorities ─────────────────────────────────────────────────────────────

  it('pode() compara o nome literal, sem prefixo e sem normalização', () => {
    store.entrar(resposta({ authorities: ['pac.paciente.vincular'] }));

    expect(store.pode('pac.paciente.vincular')).toBe(true);
    // "Quase igual" aqui seria permissão concedida por engano.
    expect(store.pode('PAC.PACIENTE.VINCULAR')).toBe(false);
    expect(store.pode('pac.paciente.vincular ')).toBe(false);
    expect(store.pode('SCOPE_pac.paciente.vincular')).toBe(false);
  });

  it('pode() é falso sem sessão', () => {
    expect(store.pode('pac.paciente.vincular')).toBe(false);
  });

  it('authorities ausentes na resposta viram lista vazia, não erro', () => {
    store.entrar(resposta({ authorities: null }));

    expect(store.user()?.authorities).toEqual([]);
    expect(store.pode('qualquer.coisa')).toBe(false);
  });

  it('nome ausente (token de serviço) não quebra a sessão', () => {
    store.entrar(resposta({ nome: null }));

    expect(store.autenticado()).toBe(true);
    expect(store.user()?.nome).toBe('');
  });
});
