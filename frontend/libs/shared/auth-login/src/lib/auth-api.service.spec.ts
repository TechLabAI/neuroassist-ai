import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { AuthApiService, LoginFalhou } from './auth-api.service';
import { AUTH_API_BASE } from './auth.config';
import { RESPOSTA_TOKEN_COORD } from '../testing/token-fixtures';

// =============================================================================
// O que estes testes protegem: o contrato com `POST /oauth2/token` do
// acs-service e a classificação do erro. A fixture foi copiada de uma resposta
// REAL do serviço (curl contra localhost:8088), não escrita a partir do que o
// nome dos campos sugere — que é como esta casa produziu 21 defeitos de
// contrato: o mock e o consumidor concordam entre si e discordam do produtor.
// =============================================================================

describe('AuthApiService', () => {
  let service: AuthApiService;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AUTH_API_BASE, useValue: '/acs-api' },
      ],
    });
    service = TestBed.inject(AuthApiService);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('posta grantType/login/senha no corpo — nunca na URL', () => {
    service.login('coord@neuroassist.dev', 'Test@1234!').subscribe();

    const req = http.expectOne('/acs-api/oauth2/token');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      grantType: 'password',
      login: 'coord@neuroassist.dev',
      senha: 'Test@1234!',
    });
    // Controle negativo do vazamento: a senha não pode aparecer na URL sob
    // nenhuma forma. Este teste fica vermelho se alguém "simplificar" para GET
    // com query string.
    expect(req.request.urlWithParams).not.toContain('Test@1234');
    req.flush(RESPOSTA_TOKEN_COORD);
  });

  it('não manda Authorization no próprio login', () => {
    service.login('a@b.dev', 'x').subscribe();
    const req = http.expectOne('/acs-api/oauth2/token');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush(RESPOSTA_TOKEN_COORD);
  });

  it('devolve a resposta do ACS campo por campo', (done) => {
    service.login('coord@neuroassist.dev', 'Test@1234!').subscribe((r) => {
      expect(r.accessToken).toBe(RESPOSTA_TOKEN_COORD.accessToken);
      expect(r.tokenType).toBe('Bearer');
      expect(r.expiresIn).toBe(900);
      expect(r.papel).toBe('COORDENADOR_CLINICA');
      expect(r.tenant).toBe(RESPOSTA_TOKEN_COORD.tenant);
      expect(r.sub).toBe(RESPOSTA_TOKEN_COORD.sub);
      expect(r.nome).toBe('Coord Dev');
      expect(r.authorities).toContain('acs.cuidador.criar');
      done();
    });
    http.expectOne('/acs-api/oauth2/token').flush(RESPOSTA_TOKEN_COORD);
  });

  it('401 vira credencial-invalida', (done) => {
    service.login('a@b.dev', 'x').subscribe({
      error: (e: LoginFalhou) => {
        expect(e).toBeInstanceOf(LoginFalhou);
        expect(e.motivo).toBe('credencial-invalida');
        done();
      },
    });
    http
      .expectOne('/acs-api/oauth2/token')
      .flush(
        { detail: 'Invalid credentials' },
        { status: 401, statusText: 'Unauthorized' },
      );
  });

  it('sem resposta (status 0) vira rede — e NÃO credencial-invalida', (done) => {
    service.login('a@b.dev', 'x').subscribe({
      error: (e: LoginFalhou) => {
        expect(e.motivo).toBe('rede');
        // Controle negativo explícito: se a classificação colapsar tudo em
        // credencial-invalida, esta linha reprova.
        expect(e.motivo).not.toBe('credencial-invalida');
        done();
      },
    });
    http
      .expectOne('/acs-api/oauth2/token')
      .error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });
  });

  it('500 vira servidor', (done) => {
    service.login('a@b.dev', 'x').subscribe({
      error: (e: LoginFalhou) => {
        expect(e.motivo).toBe('servidor');
        done();
      },
    });
    http
      .expectOne('/acs-api/oauth2/token')
      .flush('boom', { status: 500, statusText: 'Server Error' });
  });

  it('o erro propagado não carrega a senha nem o corpo da resposta', (done) => {
    service.login('a@b.dev', 'senha-secreta').subscribe({
      error: (e: LoginFalhou) => {
        expect(JSON.stringify(e.message)).not.toContain('senha-secreta');
        expect(Object.values(e)).not.toContain('senha-secreta');
        done();
      },
    });
    http
      .expectOne('/acs-api/oauth2/token')
      .flush({ detail: 'Invalid credentials' }, { status: 401, statusText: 'Unauthorized' });
  });
})
// =============================================================================
// O caminho PADRÃO é o que a implantação depende — os testes acima injetam uma
// base própria, o que prova que a base é respeitada, mas não que a default está
// certa. Sem esta verificação, mudar o padrão para algo que o nginx não roteia
// passaria por todos eles: o fallback da SPA responde 200 com HTML, então o
// sintoma em produção seria um erro de parse, não um 404.
// =============================================================================

describe('AuthApiService — caminho padrão', () => {
  let http: HttpTestingController;
  let api: AuthApiService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), AuthApiService],
    });
    http = TestBed.inject(HttpTestingController);
    api = TestBed.inject(AuthApiService);
  });

  afterEach(() => http.verify());

  it('sem base configurada, chama /oauth2/token — o mesmo caminho que o nginx do container encaminha ao gateway', () => {
    api.login('coord@neuroassist.dev', 'x').subscribe();
    http.expectOne('/oauth2/token').flush(RESPOSTA_TOKEN_COORD);
  });
});
