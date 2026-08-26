import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { AuthSessionStore } from '@neuroassist/shared/auth-session';
import { LoginPageComponent } from './login-page.component';
import { AUTH_API_BASE } from './auth.config';
import { RESPOSTA_TOKEN_COORD } from '../testing/token-fixtures';

// =============================================================================
// CONTROLE NEGATIVO — verificado à mão:
//
//   a) fazendo `mensagem('rede')` devolver o mesmo texto de
//      'credencial-invalida', o teste "backend fora não acusa senha errada"
//      reprova.
//   b) removendo a guarda `if (this.carregando()) return` de `entrar()`, o teste
//      de duplo envio reprova (chegam duas requisições).
//   c) removendo `sessao.entrar(resposta)`, o teste "registra a sessão" reprova.
//   d) removendo a validação de `destino`, o teste de redirecionamento aberto
//      reprova.
// =============================================================================

describe('LoginPageComponent', () => {
  let fixture: ComponentFixture<LoginPageComponent>;
  let comp: LoginPageComponent;
  let http: HttpTestingController;

  /**
   * `destino` chega pela URL. Aqui ele é injetado por um `ActivatedRoute` de
   * teste em vez de por uma navegação real: um componente criado por
   * `TestBed.createComponent` fica fora da árvore de rotas, e o `ActivatedRoute`
   * que ele recebe é a raiz — que só se preenche num ciclo completo de
   * navegação com outlet. O que o teste garante, então, é o que importa: de que
   * chave o componente lê e o que ele aceita como destino.
   */
  function montar(destino: string | null = null): void {
    sessionStorage.clear();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      imports: [LoginPageComponent],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([{ path: '**', children: [] }]),
        { provide: AUTH_API_BASE, useValue: '/acs-api' },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              queryParamMap: convertToParamMap(destino === null ? {} : { destino }),
            },
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(LoginPageComponent);
    comp = fixture.componentInstance;
    fixture.detectChanges();
  }

  function preencher(login = 'coord@neuroassist.dev', senha = 'Test@1234!'): void {
    comp.login.set(login);
    comp.senha.set(senha);
    fixture.detectChanges();
  }

  function submeter(): void {
    comp.entrar(new Event('submit'));
  }

  it('não chama o backend com campos vazios', () => {
    montar();
    submeter();
    http.expectNone('/acs-api/oauth2/token');
  });

  it('sucesso: registra a sessão e navega para a home', fakeAsync(() => {
    montar();
    const router = TestBed.inject(Router);
    const nav = jest.spyOn(router, 'navigateByUrl');
    preencher();
    submeter();

    http.expectOne('/acs-api/oauth2/token').flush(RESPOSTA_TOKEN_COORD);
    tick();

    const sessao = TestBed.inject(AuthSessionStore);
    expect(sessao.autenticado()).toBe(true);
    expect(sessao.user()?.papel).toBe('COORDENADOR_CLINICA');
    expect(sessao.user()?.nome).toBe('Coord Dev');
    expect(nav).toHaveBeenCalledWith('/');
  }));

  it('sucesso: limpa a senha da memória do componente', fakeAsync(() => {
    montar();
    preencher();
    submeter();
    http.expectOne('/acs-api/oauth2/token').flush(RESPOSTA_TOKEN_COORD);
    tick();
    expect(comp.senha()).toBe('');
  }));

  it('401: mostra UMA mensagem, que não diz qual metade errou', fakeAsync(() => {
    montar();
    preencher();
    submeter();
    http
      .expectOne('/acs-api/oauth2/token')
      .flush({ detail: 'Invalid credentials' }, { status: 401, statusText: 'Unauthorized' });
    tick();
    fixture.detectChanges();

    const texto = (
      fixture.nativeElement as HTMLElement
    ).querySelector('[data-testid="login-erro"]')?.textContent?.toLowerCase();

    expect(texto).toContain('e-mail ou senha incorretos');
    // O ACS responde o mesmo 401 opaco para usuário inexistente e para senha
    // errada. A tela não pode desfazer isso.
    expect(texto).not.toContain('não cadastrad');
    expect(texto).not.toContain('não existe');
    expect(texto).not.toContain('senha incorreta');
    expect(TestBed.inject(AuthSessionStore).autenticado()).toBe(false);
  }));

  it('backend fora: NÃO acusa senha errada', fakeAsync(() => {
    montar();
    preencher();
    submeter();
    http
      .expectOne('/acs-api/oauth2/token')
      .error(new ProgressEvent('error'), { status: 0, statusText: 'Unknown Error' });
    tick();
    fixture.detectChanges();

    const texto = (
      fixture.nativeElement as HTMLElement
    ).querySelector('[data-testid="login-erro"]')?.textContent?.toLowerCase();

    expect(texto).toContain('não consegui falar com o servidor');
    expect(texto).not.toContain('incorretos');
  }));

  it('não permite duplo envio', fakeAsync(() => {
    montar();
    preencher();
    submeter();
    submeter();
    submeter();
    // `expectOne` reprova se houver mais de uma requisição pendente.
    http.expectOne('/acs-api/oauth2/token').flush(RESPOSTA_TOKEN_COORD);
    tick();
  }));

  it('durante a chamada o botão fica desabilitado', fakeAsync(() => {
    montar();
    preencher();
    submeter();
    fixture.detectChanges();
    const botao = (fixture.nativeElement as HTMLElement).querySelector(
      '[data-testid="login-submit"]',
    ) as HTMLButtonElement;
    expect(botao.disabled).toBe(true);

    http.expectOne('/acs-api/oauth2/token').flush(RESPOSTA_TOKEN_COORD);
    tick();
  }));

  it('a senha nunca vai para o DOM em texto claro', fakeAsync(() => {
    montar();
    preencher();
    submeter();
    http
      .expectOne('/acs-api/oauth2/token')
      .flush({ detail: 'Invalid credentials' }, { status: 401, statusText: 'Unauthorized' });
    tick();
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).innerHTML).not.toContain('Test@1234');
  }));

  it('autopreenchimento do navegador: envia mesmo sem evento de input', fakeAsync(() => {
    montar();
    // É exatamente o que o gerenciador de senhas faz: escreve no `value` e o
    // Chrome não dispara `input`. Sem a leitura do DOM no envio, o botão fica
    // travado justamente para quem usa gerenciador de senhas.
    const raiz = fixture.nativeElement as HTMLElement;
    (raiz.querySelector('#login-email') as HTMLInputElement).value = 'coord@neuroassist.dev';
    (raiz.querySelector('#login-senha') as HTMLInputElement).value = 'Test@1234!';

    const botao = raiz.querySelector('[data-testid="login-submit"]') as HTMLButtonElement;
    expect(botao.disabled).toBe(false);

    submeter();
    const req = http.expectOne('/acs-api/oauth2/token');
    expect(req.request.body.login).toBe('coord@neuroassist.dev');
    req.flush(RESPOSTA_TOKEN_COORD);
    tick();
    expect(TestBed.inject(AuthSessionStore).autenticado()).toBe(true);
  }));

  it('respeita o destino pedido pelo guard', fakeAsync(() => {
    montar('/acs/cuidadores');
    const nav = jest.spyOn(TestBed.inject(Router), 'navigateByUrl');
    preencher();
    submeter();
    http.expectOne('/acs-api/oauth2/token').flush(RESPOSTA_TOKEN_COORD);
    tick();
    expect(nav).toHaveBeenCalledWith('/acs/cuidadores');
  }));

  it('recusa destino externo (redirecionamento aberto)', fakeAsync(() => {
    montar('https://sitefalso.example.com');
    const nav = jest.spyOn(TestBed.inject(Router), 'navigateByUrl');
    preencher();
    submeter();
    http.expectOne('/acs-api/oauth2/token').flush(RESPOSTA_TOKEN_COORD);
    tick();
    expect(nav).toHaveBeenCalledWith('/');
  }));

  it('recusa destino //outro-host', fakeAsync(() => {
    montar('//sitefalso.example.com');
    const nav = jest.spyOn(TestBed.inject(Router), 'navigateByUrl');
    preencher();
    submeter();
    http.expectOne('/acs-api/oauth2/token').flush(RESPOSTA_TOKEN_COORD);
    tick();
    expect(nav).toHaveBeenCalledWith('/');
  }));
});
