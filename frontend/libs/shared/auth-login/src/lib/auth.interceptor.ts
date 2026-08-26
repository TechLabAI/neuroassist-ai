import { inject } from '@angular/core';
import { HttpInterceptorFn } from '@angular/common/http';
import { AuthSessionStore } from '@neuroassist/shared/auth-session';
import { AUTH_BEARER_URL_PREFIXES, SKIP_AUTH_HEADER } from './auth.config';

// =============================================================================
// authBearerInterceptorFn — anexa `Authorization: Bearer <token>`.
//
// Sobre a ORDEM em `withInterceptors([...])`: a lista é a ordem de execução na
// ida. Este vem antes do `acsHttpInterceptorFn` porque a identidade é anterior
// ao contexto de empresa — mas a corretude não depende disso, e é bom entender
// por quê: `HttpRequest` é **imutável**. Cada interceptor faz `req.clone({
// setHeaders })` sobre o que recebeu, então o segundo clone preserva o header
// do primeiro. Isto é diferente do Feign no backend, onde o
// `RequestTemplate.header()` acrescenta a um objeto mutável e compartilhado.
//
// A armadilha aqui não é a ordem, é esquecer o `clone`: mexer em
// `req.headers.set(...)` e descartar o retorno compila, roda e não faz nada —
// `HttpHeaders.set()` devolve uma instância nova em vez de alterar a atual.
// =============================================================================

export const authBearerInterceptorFn: HttpInterceptorFn = (req, next) => {
  // `optional: false` de propósito: o store é `providedIn: 'root'`. Se ele
  // deixar de resolver, é bug de wiring — e um interceptor que engole isso e
  // segue sem token produziria 401 em cascata sem causa aparente.
  const sessao = inject(AuthSessionStore);
  const prefixos = inject(AUTH_BEARER_URL_PREFIXES);

  if (req.context.get(SKIP_AUTH_HEADER)) {
    return next(req);
  }

  const token = sessao.token();
  if (token === null || !ehDoNossoBackend(req.url, prefixos)) {
    return next(req);
  }

  return next(req.clone({ setHeaders: { Authorization: `Bearer ${token}` } }));
};

/**
 * A URL é de um caminho nosso?
 *
 * <p>Aceita tanto o caminho relativo (`/acs-api/...`, o caso normal) quanto a
 * URL absoluta de mesma origem — que aparece quando alguém monta a chamada com
 * `location.origin` na frente. Uma origem diferente nunca casa, e é essa a
 * parte que impede o token de sair para terceiros.
 */
function ehDoNossoBackend(url: string, prefixos: readonly string[]): boolean {
  const caminho = url.startsWith('http')
    ? extrairCaminhoSeMesmaOrigem(url)
    : url;
  if (caminho === null) return false;
  return prefixos.some((p) => caminho === p || caminho.startsWith(`${p}/`));
}

function extrairCaminhoSeMesmaOrigem(url: string): string | null {
  try {
    const alvo = new URL(url);
    // `globalThis.location` pode não existir (SSR, worker): sem origem para
    // comparar, o seguro é não anexar a credencial.
    const origem = globalThis.location?.origin;
    if (!origem || alvo.origin !== origem) return null;
    return alvo.pathname;
  } catch {
    return null;
  }
}
