// =============================================================================
// @neuroassist/evnt/testing — só para suítes de e2e.
//
// Porta separada do barrel principal pelo mesmo motivo da `auth-login/testing`:
// o barrel exporta componentes Angular, que o runner Node do Playwright não
// consegue carregar.
// =============================================================================

import { EVNT_E2E_FLAGS } from '../lib/api/evnt-e2e-flags';

export { EVNT_E2E_FLAGS };

/**
 * Script para `page.addInitScript()` que apaga a emergência ativa da semente.
 *
 * <p><b>Por que quase toda suíte precisa disto.</b> O mock do EVNT semeia uma
 * emergência ativa, e o alarme de emergência PREEMPTA a tela inteira — é o
 * comportamento correto do produto, e a suíte do EVNT existe para prová-lo. Só
 * que ele também aparece sobre `/acs`, `/pac` e `/historico`, contribuindo um
 * segundo `&lt;h1&gt;` e um `alertdialog` por cima do conteúdo. Vinte e cinco
 * cenários reprovavam por `strict mode violation` — a tela estava certa, a
 * suíte é que media a partir de um estado que não era o dela.
 *
 * <p>Suítes sobre alarme NÃO devem usar isto: para elas a emergência é o objeto.
 */
export function scriptSemAlarmeDeEmergencia(): string {
  return `(() => {
  try {
    localStorage.setItem(${JSON.stringify(EVNT_E2E_FLAGS.semEmergencia)}, 'true');
  } catch { /* localStorage bloqueado: o alarme aparece e a suíte falha dizendo por quê */ }
})()`;
}
