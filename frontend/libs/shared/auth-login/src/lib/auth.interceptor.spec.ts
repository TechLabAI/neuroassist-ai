import { TestBed } from '@angular/core/testing';
import { HttpClient, HttpContext, provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { AuthSessionStore } from '@neuroassist/shared/auth-session';
import { authBearerInterceptorFn } from './auth.interceptor';
import { AUTH_BEARER_URL_PREFIXES, SKIP_AUTH_HEADER } from './auth.config';
import { RESPOSTA_TOKEN_COORD } from '../testing/token-fixtures';

// =============================================================================
// CONTROLE NEGATIVO — verificado à mão:
//
//   a) removendo o `authBearerInterceptorFn` da lista de interceptors, o teste
//      "anexa Bearer" reprova.
//   b) trocando `next(req.clone({setHeaders}))` por `next(req)` (o erro clássico
//      de esquecer que HttpRequest é imutável), o mesmo teste reprova.
//   c) removendo o filtro por prefixo, o teste "não vaza o token para origem
//      terceira" reprova.
// =============================================================================

function comInterceptor(extra: unknown[] = []) {
  TestBed.resetTestingModule();
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(withInterceptors([authBearerInterceptorFn])),
      provideHttpClientTesting(),
      ...(extra as never[]),
    ],
  });
}

describe('authBearerInterceptorFn', () => {
  beforeEach(() => sessionStorage.clear());

  it('anexa Authorization: Bearer nas chamadas ao nosso backend', () => {
    comInterceptor();
    TestBed.inject(AuthSessionStore).entrar(RESPOSTA_TOKEN_COORD);

    TestBed.inject(HttpClient).get('/acs-api/v1/acs/cuidadores').subscribe();
    const req = TestBed.inject(HttpTestingController).expectOne(
      '/acs-api/v1/acs/cuidadores',
    );
    expect(req.request.headers.get('Authorization')).toBe(
      `Bearer ${RESPOSTA_TOKEN_COORD.accessToken}`,
    );
    req.flush({});
  });

  it('sem sessão, não inventa header', () => {
    comInterceptor();
    TestBed.inject(HttpClient).get('/acs-api/v1/x').subscribe();
    const req = TestBed.inject(HttpTestingController).expectOne('/acs-api/v1/x');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('NÃO vaza o token para uma origem terceira', () => {
    comInterceptor();
    TestBed.inject(AuthSessionStore).entrar(RESPOSTA_TOKEN_COORD);

    TestBed.inject(HttpClient).get('https://terceiro.example.com/acs-api/x').subscribe();
    const req = TestBed.inject(HttpTestingController).expectOne(
      'https://terceiro.example.com/acs-api/x',
    );
    // O caminho até casa com o prefixo — é justamente por isso que o teste
    // existe: filtrar por `includes('/acs-api')` passaria aqui.
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('respeita SKIP_AUTH_HEADER', () => {
    comInterceptor();
    TestBed.inject(AuthSessionStore).entrar(RESPOSTA_TOKEN_COORD);

    TestBed.inject(HttpClient)
      .post('/acs-api/oauth2/token', {}, { context: new HttpContext().set(SKIP_AUTH_HEADER, true) })
      .subscribe();
    const req = TestBed.inject(HttpTestingController).expectOne('/acs-api/oauth2/token');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('preserva headers postos por outros interceptors (HttpRequest é imutável)', () => {
    // Simula o `acsHttpInterceptorFn`, que roda DEPOIS e também clona.
    const depois = (req: never, next: (r: never) => unknown) =>
      next(
        (req as unknown as { clone: (o: unknown) => never }).clone({
          setHeaders: { 'X-Empresa-Id': 'emp-1' },
        }),
      );
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authBearerInterceptorFn, depois as never])),
        provideHttpClientTesting(),
      ],
    });
    TestBed.inject(AuthSessionStore).entrar(RESPOSTA_TOKEN_COORD);

    TestBed.inject(HttpClient).get('/acs-api/v1/x').subscribe();
    const req = TestBed.inject(HttpTestingController).expectOne('/acs-api/v1/x');
    expect(req.request.headers.get('Authorization')).toContain('Bearer');
    expect(req.request.headers.get('X-Empresa-Id')).toBe('emp-1');
    req.flush({});
  });

  it('a lista de prefixos é configurável e define o que recebe o token', () => {
    comInterceptor([{ provide: AUTH_BEARER_URL_PREFIXES, useValue: ['/outro'] }]);
    TestBed.inject(AuthSessionStore).entrar(RESPOSTA_TOKEN_COORD);

    TestBed.inject(HttpClient).get('/acs-api/v1/x').subscribe();
    const req = TestBed.inject(HttpTestingController).expectOne('/acs-api/v1/x');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });
});
