// =============================================================================
// @neuroassist/acs/testing — só para suítes de e2e.
//
// Porta separada do barrel principal pelo mesmo motivo da `auth-login/testing` e
// da `evnt/testing`: o barrel exporta componentes Angular, que o runner Node do
// Playwright não consegue carregar.
// =============================================================================

import {
  ACS_E2E_FLAGS,
  ACS_E2E_CUIDADORA_OUTRA_EMPRESA,
} from '../lib/api/acs-e2e-flags';

export { ACS_E2E_FLAGS, ACS_E2E_CUIDADORA_OUTRA_EMPRESA };

/**
 * Script para `page.addInitScript()` que liga uma ou mais bandeiras do ACS.
 *
 * <p>Tem de ser `addInitScript` e não um `evaluate` depois do `goto`: os
 * adaptadores mock leem a bandeira na PRIMEIRA chamada, disparada pelo
 * `ngOnInit` do componente. Escrever depois da navegação chega tarde.
 *
 * <p>Recebe os valores de `ACS_E2E_FLAGS` — nunca a string crua —, que é o que
 * faz um nome errado virar erro de compilação em vez de bandeira inerte.
 */
export function scriptLigarBandeirasAcs(
  ...bandeiras: readonly (typeof ACS_E2E_FLAGS)[keyof typeof ACS_E2E_FLAGS][]
): string {
  return `(() => {
  try {
    for (const b of ${JSON.stringify(bandeiras)}) localStorage.setItem(b, 'true');
  } catch { /* localStorage bloqueado: o cenário falha dizendo por quê */ }
})()`;
}
