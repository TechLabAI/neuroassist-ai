/**
 * Helpers e fixtures para os cenários E2E do TREI (FE-027..031, P9).
 *
 * Estes utilitários configuram o ambiente Playwright para:
 *   - Semear a sessão da cuidadora com authorities TREI no sessionStorage
 *     (lido pelo AuthSessionStore no construtor Angular, ANTES do bootstrap)
 *   - Controlar comportamento dos mock adapters via localStorage flags
 *   - Navegar para rotas TREI aguardando estabilidade do componente raiz
 *
 * REGRA: todos os `addInitScript` devem ser chamados ANTES de `page.goto()`.
 *   O AuthSessionStore lê o sessionStorage no construtor — semear depois do
 *   goto não tem efeito nos guards.
 *
 * AUTHORITIES TREI (spec-derived, não verificadas contra backend — trei-service
 * ainda não está implantado):
 *   treinamento.sessao.conduzir    → treiGuard (acesso a /treinamento)
 *   treinamento.no.propor          → treiProporGuard (acesso a /sessao/propostas)
 *   treinamento.no.aprovar.cuidadora → aprovação de nó pelo lado cuidadora
 */

import type { Page } from '@playwright/test';

// ---------------------------------------------------------------------------
// Constantes de sessão
// ---------------------------------------------------------------------------

/** Chave usada pelo AuthSessionStore — mantida em sincronia com auth-session.ts. */
const SESSION_STORAGE_KEY = 'neuroassist.session';

/** E2E tenant id (mesmo usado pelas fixtures de token-fixtures.ts). */
export const TENANT_ID_E2E = 'e2e00000-0000-0000-0000-000000000001';

/** E2E caregiver id — TREI full-access fixture. */
export const CAREGIVER_ID_TREINAMENTO = 'e2e00000-0000-0000-0000-000000000020';

/** E2E caregiver id — TREI sem treinamento.no.propor (para teste G1). */
export const CAREGIVER_ID_SEM_PROPOR = 'e2e00000-0000-0000-0000-000000000021';

/** pacienteId mock usado pelo TrainingSessionMockAdapter. */
export const PACIENTE_ID_E2E = 'pac-marcio-e2e-001';

// ---------------------------------------------------------------------------
// Builders de payload de sessão
// ---------------------------------------------------------------------------

function buildSessionPayload(opts: {
  id: string;
  nome: string;
  papel: string;
  authorities: string[];
  validoPorSegundos?: number;
}): string {
  const expiraEm = new Date(
    Date.now() + (opts.validoPorSegundos ?? 3600) * 1000,
  ).toISOString();
  return JSON.stringify({
    token: `fixture-token-${opts.id}`,
    user: {
      id: opts.id,
      nome: opts.nome,
      papel: opts.papel,
      tenantId: TENANT_ID_E2E,
      authorities: opts.authorities,
      expiraEm,
    },
  });
}

// ---------------------------------------------------------------------------
// Scripts de semeadura (addInitScript)
// ---------------------------------------------------------------------------

/**
 * Semeia uma sessão com TODAS as authorities TREI.
 * Usar como padrão para a maioria dos testes TREI.
 *
 *   await page.addInitScript(scriptSemearSessaoTreinamento());
 */
export function scriptSemearSessaoTreinamento(validoPorSegundos = 3600): string {
  const payload = buildSessionPayload({
    id: CAREGIVER_ID_TREINAMENTO,
    nome: 'Cuidadora TREI Dev',
    papel: 'CUIDADORA',
    authorities: [
      'ROLE_CAREGIVER',
      'treinamento.sessao.conduzir',
      'treinamento.no.propor',
      'treinamento.no.aprovar.cuidadora',
    ],
    validoPorSegundos,
  });
  return `(() => {
  try {
    sessionStorage.setItem(${JSON.stringify(SESSION_STORAGE_KEY)}, ${JSON.stringify(payload)});
  } catch { /* sessionStorage bloqueado — o teste falha no guard, e isso é informação */ }
})()`;
}

/**
 * Semeia uma sessão SEM treinamento.no.propor.
 * Usar nos cenários G1 — o treiProporGuard deve redirecionar para /treinamento/sessao.
 *
 *   await page.addInitScript(scriptSemearSessaoSemPropor());
 */
export function scriptSemearSessaoSemPropor(validoPorSegundos = 3600): string {
  const payload = buildSessionPayload({
    id: CAREGIVER_ID_SEM_PROPOR,
    nome: 'Cuidadora Sem Propor Dev',
    papel: 'CUIDADORA',
    authorities: [
      'ROLE_CAREGIVER',
      'treinamento.sessao.conduzir',
      // 'treinamento.no.propor' deliberadamente ausente — G1 invariant
      'treinamento.no.aprovar.cuidadora',
    ],
    validoPorSegundos,
  });
  return `(() => {
  try {
    sessionStorage.setItem(${JSON.stringify(SESSION_STORAGE_KEY)}, ${JSON.stringify(payload)});
  } catch { /* sessionStorage bloqueado — o teste falha no guard, e isso é informação */ }
})()`;
}

/**
 * Semeia uma sessão SEM nenhuma authority de treinamento.
 * Usar no cenário onde treiGuard deve redirecionar para /dashboard.
 */
export function scriptSemearSessaoSemConduzir(validoPorSegundos = 3600): string {
  const payload = buildSessionPayload({
    id: 'e2e00000-0000-0000-0000-000000000022',
    nome: 'Cuidadora Sem TREI Dev',
    papel: 'CUIDADORA',
    authorities: [
      'ROLE_CAREGIVER',
      // Sem nenhuma authority de treinamento — treiGuard redireciona para /dashboard
    ],
    validoPorSegundos,
  });
  return `(() => {
  try {
    sessionStorage.setItem(${JSON.stringify(SESSION_STORAGE_KEY)}, ${JSON.stringify(payload)});
  } catch { /* sessionStorage bloqueado */ }
})()`;
}

// ---------------------------------------------------------------------------
// Scripts de controle dos mock adapters (e2eFlag via localStorage)
// ---------------------------------------------------------------------------

/**
 * Ativa modo rápido (200ms delay em vez de 1200ms).
 * Usar em beforeEach para testes que não testam timing.
 */
export function scriptModoRapido(): string {
  return `(() => {
  try { localStorage.setItem('__TREI_E2E_FAST__', 'true'); } catch {}
})()`;
}

/**
 * Ativa simulação de patient-not-found (G4 — SPEC-07 / FE-025).
 * O TrainingSessionMockAdapter retorna HTTP 404 quando esta flag está ativa.
 */
export function scriptPatientNotFound(): string {
  return `(() => {
  try { localStorage.setItem('__TREI_E2E_PATIENT_NOT_FOUND__', 'true'); } catch {}
})()`;
}

/**
 * Ativa simulação de LLM unavailable (503 — SPEC-07 / erros e degradação).
 * O ProposalTurnMockAdapter lança 503 quando esta flag está ativa.
 */
export function scriptLlmFail(): string {
  return `(() => {
  try { localStorage.setItem('__TREI_E2E_LLM_FAIL__', 'true'); } catch {}
})()`;
}

/**
 * Ativa simulação de conflito ao descartar (409 — SPEC-07).
 * O DraftNodeMockAdapter lança 409 na ação discard quando ativa.
 */
export function scriptDiscardFail(): string {
  return `(() => {
  try { localStorage.setItem('__TREI_E2E_DISCARD_FAIL__', 'true'); } catch {}
})()`;
}

// ---------------------------------------------------------------------------
// Helpers de navegação
// ---------------------------------------------------------------------------

/**
 * Navega para a tela de sessão de treinamento e aguarda o componente estabilizar.
 * Presume que a sessão já foi semeada via addInitScript.
 * Aguarda o authoring-banner (renderizado pelo TreiRootComponent em toda rota TREI).
 */
export async function gotoSessao(page: Page): Promise<void> {
  await page.goto('/treinamento/sessao');
  await page.waitForSelector('[data-testid="authoring-banner"]', {
    timeout: 10_000,
  });
}

/**
 * Navega para a tela de revisão de propostas.
 * Presume autorização com scope treinamento.no.propor já semeada.
 */
export async function gotoPropostas(page: Page): Promise<void> {
  await page.goto('/treinamento/sessao/propostas');
  await page.waitForSelector('[data-testid="authoring-banner"]', {
    timeout: 10_000,
  });
}

/**
 * Aguarda o estado vazio aparecer na tela de sessão.
 * Indica que nenhuma sessão está ativa.
 */
export async function aguardarEstadoVazio(page: Page): Promise<void> {
  await page.waitForSelector('[data-testid="empty-state"]', {
    timeout: 6_000,
  });
}

/**
 * Inicia uma sessão de treinamento clicando no CTA do estado vazio.
 * Aguarda o chat-composer aparecer, indicando sessão ativa.
 */
export async function iniciarSessao(page: Page): Promise<void> {
  await page.getByTestId('start-session-btn').click();
  await page.waitForSelector('[data-testid="chat-composer"]', {
    timeout: 4_000,
  });
}

/**
 * Envia um turno de conversa e aguarda as propostas do assistente.
 * Usa o modo rápido (requer __TREI_E2E_FAST__ já ativo via addInitScript).
 */
export async function enviarTurnoEAguardarPropostas(
  page: Page,
  texto: string,
): Promise<void> {
  await page.getByTestId('composer-input').fill(texto);
  await page.getByTestId('send-btn').click();
  // Aguarda o indicador de pensamento aparecer
  await page.waitForSelector('[data-testid="assistant-thinking"]', {
    timeout: 2_000,
  });
  // Aguarda as propostas aparecerem
  await page.waitForSelector('[data-testid="proposals-list"]', {
    timeout: 4_000,
  });
}
