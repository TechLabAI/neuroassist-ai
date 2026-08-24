import { EnvironmentProviders, makeEnvironmentProviders } from '@angular/core';
import { Routes } from '@angular/router';
import { AUTH_API_BASE, AUTH_BEARER_URL_PREFIXES } from './auth.config';
import { sessaoAusenteGuard } from './auth.guard';

// =============================================================================
// Wiring da autenticação.
//
// Note o que NÃO existe aqui: um `adapter: 'mock' | 'http'`. Todos os módulos
// (`provideAcs`, `providePac`, …) têm esse par, e a autenticação deliberadamente
// não tem. Um login em mock seria um login que aceita qualquer senha — a única
// coisa que a tela existe para não fazer. Se o backend não estiver no ar, o
// resultado correto é "não consegui falar com o servidor", não "entrou".
// =============================================================================

export interface AuthConfig {
  /**
   * Prefixo das chamadas ao acs-service. Caminho relativo à origem do app —
   * o navegador não deve conhecer a porta do serviço (ver `auth.config.ts`).
   */
  apiBase?: string;
  /** Prefixos de URL autorizados a receber o `Authorization`. */
  bearerUrlPrefixes?: readonly string[];
}

export function provideAuth(config: AuthConfig = {}): EnvironmentProviders {
  const apiBase = config.apiBase ?? '/acs-api';
  const prefixes = config.bearerUrlPrefixes ?? [apiBase, '/api'];
  return makeEnvironmentProviders([
    { provide: AUTH_API_BASE, useValue: apiBase },
    { provide: AUTH_BEARER_URL_PREFIXES, useValue: prefixes },
  ]);
}

/**
 * Rotas da autenticação. Ficam **fora** do shell: a tela de login não pode
 * aparecer dentro do menu que ela própria é pré-requisito para existir.
 */
export const AUTH_ROUTES: Routes = [
  {
    path: 'login',
    canActivate: [sessaoAusenteGuard],
    loadComponent: () => import('./login-page.component').then((m) => m.LoginPageComponent),
    title: 'Entrar',
  },
];
