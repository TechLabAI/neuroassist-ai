import type { TokenResponse } from '@neuroassist/shared/auth-session';
import { RESPOSTA_TOKEN_COORD, RESPOSTA_TOKEN_CUIDADORA } from './token-fixtures';

// =============================================================================
// Semear sessão nos testes de ponta a ponta.
//
// POR QUE ISTO EXISTE: todas as rotas dentro do shell passaram a exigir sessão,
// e as suítes de e2e navegam direto para `/pac`, `/acs`, `/eventos`… sem entrar.
// Sem semear, elas caem em `/login` e reprovam.
//
// A alternativa seria manter um bypass no guard "só para teste". Não: um guard
// com bypass é o que já existe no `acsCoordinatorGuard`, e é por isso que /acs
// está aberto até hoje. O teste é que se adapta à produção, não o contrário.
//
// Uso no Playwright, ANTES do primeiro `goto`:
//
//   import { scriptSemearSessao } from '@neuroassist/shared/auth-login';
//   await page.addInitScript(scriptSemearSessao());
//
// (`addInitScript` roda antes de qualquer script da página — o `AuthSessionStore`
// lê o `sessionStorage` no construtor, então semear depois do `goto` não adianta.)
// =============================================================================

/** Chave usada pelo AuthSessionStore. Mantida em sincronia com auth-session.ts. */
const STORAGE_KEY = 'neuroassist.session';

/**
 * Constrói o conteúdo que o `AuthSessionStore` espera encontrar.
 *
 * <p>O token é a fixture: **não é um JWT válido** e não precisa ser. Quem valida
 * assinatura é o resource server; se o e2e chamar backend real, ele tomará 401 —
 * e é esse o comportamento correto, não algo a contornar aqui.
 */
export function conteudoSessaoSemeada(
  validoPorSegundos = 3600,
  resposta: TokenResponse = RESPOSTA_TOKEN_COORD,
): string {
  return JSON.stringify({
    token: resposta.accessToken,
    user: {
      id: resposta.sub,
      nome: resposta.nome,
      papel: resposta.papel,
      tenantId: resposta.tenant,
      authorities: resposta.authorities,
      expiraEm: new Date(Date.now() + validoPorSegundos * 1000).toISOString(),
    },
  });
}

/**
 * Script para `page.addInitScript()`. Não depende de nada do bundle da página.
 *
 * <p>É função e não constante de módulo de propósito: a validade é contada a
 * partir de `Date.now()`, e uma constante avaliada no import envelheceria junto
 * com o processo do runner.
 */
export function scriptSemearSessao(validoPorSegundos = 3600): string {
  return `(() => {
  try {
    sessionStorage.setItem(${JSON.stringify(STORAGE_KEY)}, ${JSON.stringify(
      conteudoSessaoSemeada(validoPorSegundos),
    )});
  } catch { /* sessionStorage bloqueado: o teste falha no guard, e isso é informação */ }
})()`;
}

/**
 * Script que semeia uma sessão de CUIDADORA em vez de coordenadora.
 *
 * <p>Para os cenários cuja regra É a diferença de papel — a cuidadora não tem
 * `edge.manage`, logo não deve ver botão de gerenciar dispositivo. Semear a
 * coordenadora e depois "fingir" cuidadora por um flag prova nada: o produto
 * decide pelas authorities do token, e é o token que precisa ser outro.
 */
export function scriptSemearSessaoCuidadora(validoPorSegundos = 3600): string {
  return `(() => {
  try {
    sessionStorage.setItem(${JSON.stringify(STORAGE_KEY)}, ${JSON.stringify(
      conteudoSessaoSemeada(validoPorSegundos, RESPOSTA_TOKEN_CUIDADORA),
    )});
  } catch { /* sessionStorage bloqueado: o teste falha no guard, e isso é informação */ }
})()`;
}
