import { TestBed } from '@angular/core/testing';
import { Router, UrlTree, type ActivatedRouteSnapshot, type RouterStateSnapshot } from '@angular/router';
import { provideRouter } from '@angular/router';
import { runInInjectionContext, Injector } from '@angular/core';
import { AuthSessionStore } from '@neuroassist/shared/auth-session';
import { sessaoAusenteGuard, sessaoRequeridaGuard } from './auth.guard';
import { RESPOSTA_TOKEN_COORD } from '../testing/token-fixtures';

// =============================================================================
// Guards de sessão.
//
// CONTROLE NEGATIVO — verificado à mão antes de declarar isto verde:
//
//   a) trocando o corpo de `sessaoRequeridaGuard` por `return true`
//      (que é EXATAMENTE o "Wave-0 bypass" do `acsCoordinatorGuard`), os dois
//      primeiros testes reprovam: "redireciona para /login" e "preserva o
//      destino".
//   b) removendo a checagem de expiração (aceitando `token !== null` em vez de
//      `autenticado()`), o teste do token vencido reprova.
//   c) trocando `sessaoAusenteGuard` por `return true`, o teste "quem já entrou
//      não vê o formulário" reprova.
//
// Sem esses três, este arquivo seria um guarda testando o próprio otimismo.
// =============================================================================

function rodarGuard(
  guard: typeof sessaoRequeridaGuard,
  url: string,
): boolean | UrlTree {
  const injector = TestBed.inject(Injector);
  return runInInjectionContext(injector, () =>
    guard(
      {} as ActivatedRouteSnapshot,
      { url } as RouterStateSnapshot,
    ),
  ) as boolean | UrlTree;
}

describe('guards de sessão', () => {
  let sessao: AuthSessionStore;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({ providers: [provideRouter([])] });
    sessao = TestBed.inject(AuthSessionStore);
    sessao.sair();
  });

  describe('sessaoRequeridaGuard', () => {
    it('sem sessão: manda para /login', () => {
      const r = rodarGuard(sessaoRequeridaGuard, '/pac');
      expect(r).toBeInstanceOf(UrlTree);
      expect(TestBed.inject(Router).serializeUrl(r as UrlTree)).toContain('/login');
    });

    it('sem sessão: preserva o destino pretendido', () => {
      const r = rodarGuard(sessaoRequeridaGuard, '/acs/cuidadores/novo');
      const url = TestBed.inject(Router).serializeUrl(r as UrlTree);
      expect(url).toContain('destino=');
      expect(decodeURIComponent(url)).toContain('/acs/cuidadores/novo');
    });

    it('com sessão válida: passa', () => {
      sessao.entrar(RESPOSTA_TOKEN_COORD);
      expect(rodarGuard(sessaoRequeridaGuard, '/pac')).toBe(true);
    });

    it('token vencido não é sessão: manda para /login', () => {
      // expiresIn negativo = já expirou. É o caso que um guard escrito como
      // `token !== null` deixa passar — e a pessoa cai numa tela que toma 401.
      sessao.entrar({ ...RESPOSTA_TOKEN_COORD, expiresIn: -1 });
      expect(rodarGuard(sessaoRequeridaGuard, '/pac')).toBeInstanceOf(UrlTree);
    });

    it('NÃO tem bypass: token de sessão ausente bloqueia em vez de liberar', () => {
      // O contraponto direto ao acsCoordinatorGuard, que devolve `true` quando
      // não está configurado. Aqui "não configurado" é "não autenticado".
      expect(rodarGuard(sessaoRequeridaGuard, '/acs')).not.toBe(true);
    });
  });

  describe('sessaoAusenteGuard', () => {
    it('sem sessão: deixa ver o formulário', () => {
      expect(rodarGuard(sessaoAusenteGuard, '/login')).toBe(true);
    });

    it('quem já entrou não vê o formulário: vai para a home', () => {
      sessao.entrar(RESPOSTA_TOKEN_COORD);
      const r = rodarGuard(sessaoAusenteGuard, '/login');
      expect(r).toBeInstanceOf(UrlTree);
      expect(TestBed.inject(Router).serializeUrl(r as UrlTree)).toBe('/');
    });
  });
});
