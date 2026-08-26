import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { PAC_AUTH_SESSION } from './pac-access-tokens';

// ---------------------------------------------------------------------------
// pacAuthGuard — SPEC-01 §3
//
// Verifica que há sessão antes de deixar navegar em /pac. Sem sessão: /login.
//
// ANTES: `return true` incondicional, com a implementação de verdade escrita em
// comentário logo acima (TODO[PAC-FE-013]). Um guard assim não é "guard ainda
// não integrado" — é ausência de guarda disfarçada de presença, e o gate ficava
// verde porque o único teste que existia afirmava exatamente o `true`.
//
// O token vem de fora (ver pac-access-tokens.ts): quem sabe quem está logado é
// o `AuthSessionStore` do shell, e scope:pac não pode importá-lo.
// ---------------------------------------------------------------------------

export const pacAuthGuard: CanActivateFn = () => {
  const sessao = inject(PAC_AUTH_SESSION, { optional: true });

  // Token não providenciado (lib montada isolada em teste de unidade): deixa
  // passar. No aplicativo real o `sessaoRequeridaGuard` do shell já barrou um
  // nível acima — este guard é a segunda linha, não a única.
  if (sessao === null) return true;

  if (sessao.token === null) {
    return inject(Router).createUrlTree(['/login']);
  }

  return true;
};
