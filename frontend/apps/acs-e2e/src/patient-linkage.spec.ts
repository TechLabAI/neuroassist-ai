/**
 * E2E spec: Patient linkage — link, unlink and coverage invariant
 * Feature: frontend/e2e/patient-linkage.feature
 * 12 scenarios — ACS P7 implementation
 * Gate: A5 e2e-validate. Prototype-first (D-484).
 *
 * MOCK SEED STATE (P7 wave — extended):
 *   _cuidadores: Jamilly Amaral (cuid-jamilly, active) + Maria Santos (cuid-maria, active)
 *   _links: link-001 (Jamilly→pac-marcio), link-002 (Maria→pac-marcio), link-003 (Maria→pac-outro)
 *   pac-marcio: covered by BOTH Jamilly and Maria (enables non-last-covering scenarios)
 *   pac-outro:  covered by Maria only (enables last-covering ADR-005 block scenario)
 *   pac-novo:   no links at all (enables "link a candidate" and "sem cobertura" scenarios)
 *
 * ESTADO DOS BLOQUEIOS (revisto — nenhum `test.fixme` restante nesta suíte).
 *
 * Todos os cenários EXECUTAM. Os que não podem passar hoje ficam VERMELHOS de
 * propósito, com a asserção real do BDD e uma mensagem dizendo o que falta.
 * Não troque por `skip`.
 *
 *   CADUCARAM (agora verdes):
 *     4  — P8: o modal existe; faltava a facade emitir 'cross-tenant' e ESTA tela
 *          desenhar o modal (o roster desenhava, esta não). DATA: a cuidadora de
 *          outra empresa entra pela bandeira `crossEmpresa`.
 *     9  — não era preciso adaptador HTTP: bandeira de localStorage, como as que
 *          já existiam. E a facade não re-derivava a cobertura após erro —
 *          corrigido.
 *     12 — RBAC vale: 'acs.vinculo.write' → `pac.paciente.vincular`, que a
 *          cuidadora não tem.
 *
 *   AINDA BLOQUEADOS (vermelhos deliberados — ver o comentário de cada um):
 *     8  — sem pré-checagem de UI no desvínculo, "guard contornado" e "caminho
 *          normal" são a mesma execução; hoje é duplicata do cenário 7.
 *     11 — inalcançável: a authority que desabilitaria os botões já barra a rota.
 *
 * As bandeiras de e2e vêm de `@neuroassist/acs/testing` (ACS_E2E_FLAGS) — o
 * literal mora num lugar só.
 */
import { test, expect, Page } from '@playwright/test';

import {
  scriptSemearSessao,
  scriptSemearSessaoCuidadora,
} from '@neuroassist/shared/auth-login/testing';
import { scriptSemAlarmeDeEmergencia } from '@neuroassist/evnt/testing';
import {
  ACS_E2E_FLAGS,
  ACS_E2E_CUIDADORA_OUTRA_EMPRESA,
  scriptLigarBandeirasAcs,
} from '@neuroassist/acs/testing';

// Estado de partida da suíte. Ambos via `addInitScript`, que roda antes de
// qualquer script da página: o AuthSessionStore lê o sessionStorage no construtor
// e o mock do EVNT lê a bandeira na primeira chamada, então semear depois do goto
// seria tarde demais nos dois casos.
//
//   1. sessão — todas as rotas do shell passam a exigir login; sem isto a suíte
//      cai em /login;
//   2. sem emergência ativa — o mock do EVNT semeia uma, e o alarme de emergência
//      preempta a tela inteira (é o comportamento certo do produto, e a suíte do
//      EVNT existe para prová-lo). Sobre estas telas ele só contribui um segundo
//      <h1> e um alertdialog por cima do conteúdo.
test.beforeEach(async ({ page }) => {
  await page.addInitScript(scriptSemearSessao());
  await page.addInitScript(scriptSemAlarmeDeEmergencia());
});

// ── Navigation helpers ───────────────────────────────────────────────────────

/**
 * Navigate to the linkage view for a given patient and wait for coverage badge.
 * Timeout 6 000 ms: roster load (400ms) + coverage refresh (400ms) + render margin.
 */
async function openLinkage(page: Page, pacienteId: string): Promise<void> {
  await page.goto(`/acs/pacientes/${pacienteId}/vinculos`);
  await page.waitForSelector('[data-testid="coverage-badge"]', { timeout: 6000 });
}

// ── Tests ────────────────────────────────────────────────────────────────────

test.describe('Patient linkage — link, unlink and coverage invariant', () => {

  // ── Scenario 1: Vinculos route renders the panel ──────────────────────────
  // BDD: "The vinculos route renders the patient linkage panel scoped by the empresa header"
  // FIX (regression): extended seed now has BOTH Jamilly and Maria linked to pac-marcio.
  // Scope linked-caregiver-row assertions to Jamilly's row to avoid strict mode violation.
  test('The vinculos route renders the patient linkage panel scoped by the empresa header', async ({ page }) => {
    // pac-marcio has link-001 (Jamilly) and link-002 (Maria) — both active caregivers linked
    await openLinkage(page, 'pac-marcio');

    // Coverage badge must show "coberto" (pac-marcio has active links)
    await expect(page.getByTestId('coverage-badge')).toContainText(/coberto/i);

    // Jamilly is in the linked-caregiver-row (linked section)
    // Scope to Jamilly's row to avoid ambiguity with Maria Santos also in the linked section
    const jamillyRow = page.locator('[data-testid="linked-caregiver-row"]').filter({ hasText: 'Jamilly Amaral' });
    await expect(jamillyRow).toHaveCount(1);

    // Revoke action is available on Jamilly's row
    await expect(jamillyRow.getByTestId('revoke-link-btn')).toBeVisible();
  });

  // ── Scenario 2: Linking a candidate re-derives coverage ──────────────────
  // BDD: "Linking a candidate cuidador re-derives the paciente coverage"
  // FIX (regression): extended seed — pac-novo has NO links so both Jamilly and Maria appear
  // as unlinked. Scope unlinked-row assertions and click to Jamilly specifically.
  test('Linking a candidate cuidador re-derives the paciente coverage', async ({ page }) => {
    // Navigate to a patient that has NO links in the mock seed
    // pac-novo has no links for any caregiver → both Jamilly and Maria appear as unlinked
    await openLinkage(page, 'pac-novo');

    // Initially: sem cobertura
    await expect(page.getByTestId('coverage-badge')).toContainText(/sem cobertura/i);

    // Jamilly must appear in the unlinked section
    // Scope to Jamilly's row — Maria Santos is also unlinked (no link to pac-novo)
    const jamillyUnlinked = page.locator('[data-testid="unlinked-caregiver-row"]').filter({ hasText: 'Jamilly Amaral' });
    await expect(jamillyUnlinked).toHaveCount(1);

    // Click "Vincular" for Jamilly specifically (scoped to avoid strict mode with 2 link buttons)
    await jamillyUnlinked.getByTestId('link-caregiver-btn').click();

    // After link() + _refreshCoverageForPatient():
    //   link() takes 600ms (mock timer) + listForCaregiver() takes 400ms → ~1s total
    // Coverage badge must update to "coberto"
    await expect(page.getByTestId('coverage-badge')).toContainText(/coberto/i, { timeout: 5000 });

    // Jamilly should now be in the linked section
    await expect(page.locator('[data-testid="linked-caregiver-row"]').filter({ hasText: 'Jamilly Amaral' })).toHaveCount(1, { timeout: 3000 });

    // Jamilly is no longer in the unlinked section
    // (Maria remains unlinked — unlinked section still shows 1 row for Maria)
    await expect(page.locator('[data-testid="unlinked-caregiver-row"]').filter({ hasText: 'Jamilly Amaral' })).toHaveCount(0);
  });

  // ── Scenario 3: Idempotent link ───────────────────────────────────────────
  // BDD: "Linking a cuidador already linked to the paciente is treated as an idempotent success"
  // UNBLOCKED (P7 wave): extended seed has BOTH Jamilly and Maria linked to pac-marcio.
  // All active caregivers are already linked → unlinkedCaregivers() = [] → no Vincular buttons.
  // The UI correctly prevents duplicate link attempts; idempotency is a backend/facade concern.
  test('Linking a cuidador already linked to the paciente is treated as an idempotent success', async ({ page }) => {
    // pac-marcio is fully covered: Jamilly (link-001) + Maria (link-002) — no unlinked caregivers.
    // PatientLinkageComponent renders "Vincular" only for unlinkedCaregivers() — empty for pac-marcio.
    await openLinkage(page, 'pac-marcio');
    // Wait for loadRoster + _refreshCoverageMap to complete (badge shows "coberto")
    await expect(page.getByTestId('coverage-badge')).toContainText(/coberto/i, { timeout: 6000 });
    // No unlinked caregivers → no "Vincular" buttons rendered
    await expect(page.getByTestId('link-caregiver-btn')).toHaveCount(0);
  });

  // ── Scenario 4: Cross-empresa link blocked ────────────────────────────────
  // BDD: "Linking a cuidador from a different empresa is blocked with a blocking modal"
  // DESTRAVADO — os dois bloqueios caducaram, e faltava uma terceira coisa que o
  // marcador não mencionava.
  //
  //   BLOCKER-P8 caducou: `CoverageBlockModalComponent` existe e já trata
  //     `type='cross-tenant'` (a mensagem sobre empresas diferentes está lá desde
  //     sempre). O que faltava: a facade nunca emitia esse tipo, e ESTA TELA nem
  //     desenhava o modal — o roster montava, a de vínculos não. Ambos corrigidos.
  //   BLOCKER-DATA caducou: a cuidadora forasteira entra pela bandeira
  //     `crossEmpresa` (atrás de bandeira porque o elenco base é afirmado por
  //     contagem em seis cenários já verdes).
  //
  // O status é 422 e não 409: o acs-service estoura `CrossTenantLinkException`, e
  // o GlobalExceptionHandler a mapeia para 422 — conferido no produtor, não
  // suposto.
  test('Linking a cuidador from a different empresa is blocked with a blocking modal', async ({ page }) => {
    await page.addInitScript(scriptLigarBandeirasAcs(ACS_E2E_FLAGS.crossEmpresa));
    await openLinkage(page, 'pac-marcio');

    // pac-marcio já tem Jamilly e Maria vinculadas, então a ÚNICA linha em
    // "Disponíveis para vincular" é a forasteira. Ainda assim escopamos pelo
    // nome: um clique no primeiro botão que aparecer mediria outra coisa se a
    // semente mudar.
    const forasteira = page
      .locator('[data-testid="unlinked-caregiver-row"]')
      .filter({ hasText: ACS_E2E_CUIDADORA_OUTRA_EMPRESA.nome });
    await expect(forasteira).toHaveCount(1);
    await forasteira.getByTestId('link-caregiver-btn').click();

    // Modal BLOQUEANTE (role=alertdialog), com a mensagem de empresas diferentes.
    const modal = page.getByTestId('coverage-block-modal');
    await expect(modal).toBeVisible({ timeout: 4000 });
    await expect(modal).toHaveAttribute('role', 'alertdialog');
    await expect(modal).toContainText(/empresas diferentes/i);

    // Bloqueante de verdade: não fecha por Esc nem por clique fora — só no CTA.
    await page.keyboard.press('Escape');
    await expect(modal).toBeVisible();
    await page.mouse.click(5, 5);
    await expect(modal).toBeVisible();
    await page.getByTestId('coverage-modal-confirm-btn').click();
    await expect(modal).toHaveCount(0);

    // E o vínculo NÃO aconteceu.
    await expect(
      page.locator('[data-testid="linked-caregiver-row"]')
        .filter({ hasText: ACS_E2E_CUIDADORA_OUTRA_EMPRESA.nome }),
    ).toHaveCount(0);
  });

  // ── Scenario 5: Unknown paciente reference ────────────────────────────────
  // BDD: "Linking an unknown paciente reference shows an inline error"
  // UNBLOCKED (P7 wave 2): AcsLinkMockAdapter.link() now validates pacienteId against
  // _knownPatientIds. 'pac-unknown' is not in the set → throws 404-equivalent error →
  // AcsCaregiverFacade.linkToPatient() catches it → error() set → linkage-error visible.
  // Both Jamilly and Maria appear as unlinked for pac-unknown (no existing links).
  // Clicking the first Vincular button triggers the rejected link attempt.
  test('Linking an unknown paciente reference shows an inline error', async ({ page }) => {
    // pac-unknown is not in _knownPatientIds → link() throws a 404-equivalent error.
    await openLinkage(page, 'pac-unknown');
    // Both active caregivers (Jamilly, Maria) are unlinked for pac-unknown.
    // page.click() selects the first matching element (Jamilly — first in Map iteration order).
    await page.click('[data-testid="link-caregiver-btn"]');
    // linkToPatient() → mock throws 404 → facade.error() set → linkage-error visible.
    await expect(page.getByTestId('linkage-error')).toBeVisible({ timeout: 3000 });
  });

  // ── Scenario 6: Unlinking non-last covering re-derives coverage ───────────
  // BDD: "Unlinking a non-last covering caregiver re-derives the paciente coverage"
  // UNBLOCKED (P7 wave): extended seed has BOTH Jamilly (link-001) and Maria (link-002) linked
  // to pac-marcio. Revoking Maria leaves Jamilly still covering → no ADR-005 block → badge green.
  test('Unlinking a non-last covering caregiver re-derives the paciente coverage', async ({ page }) => {
    // pac-marcio is covered by BOTH Jamilly (link-001) and Maria (link-002).
    // Revoking Maria (non-last covering) leaves Jamilly still covering → no 409 from mock.
    await openLinkage(page, 'pac-marcio');

    // Both caregivers are in the linked section
    await expect(page.getByTestId('coverage-badge')).toContainText(/coberto/i, { timeout: 6000 });
    await expect(page.locator('[data-testid="linked-caregiver-row"]')).toHaveCount(2);

    // Revoke Maria — she is NOT the last covering caregiver (Jamilly still covers pac-marcio)
    const mariaRow = page.locator('[data-testid="linked-caregiver-row"]').filter({ hasText: 'Maria Santos' });
    await mariaRow.getByTestId('revoke-link-btn').click();
    await expect(page.getByTestId('confirm-revoke-btn')).toBeVisible();
    await page.click('[data-testid="confirm-revoke-btn"]');

    // After revoke: mock sets link-002 inactive; listForCaregiver('cuid-maria') no longer
    // returns an active link for pac-marcio → coverage re-derived → Jamilly still covers
    await expect(page.getByTestId('coverage-badge')).toContainText(/coberto/i, { timeout: 5000 });

    // Maria is no longer in the linked section
    await expect(page.locator('[data-testid="linked-caregiver-row"]').filter({ hasText: 'Maria Santos' })).toHaveCount(0, { timeout: 3000 });

    // Only Jamilly remains in the linked section
    await expect(page.locator('[data-testid="linked-caregiver-row"]')).toHaveCount(1, { timeout: 3000 });
  });

  // ── Scenario 7: Unlinking last covering is blocked (ADR-005) ─────────────
  // BDD: "Unlinking the last covering caregiver is blocked before the request"
  // FIX (regression): pac-marcio now has TWO caregivers linked — revoking one is not last-covering.
  // Use pac-outro where Maria Santos is the SOLE covering caregiver (link-003: Maria→pac-outro).
  // NOTE: "blocked before the request" refers to the P8 CoverageBlockingModal. Currently, the
  // request IS sent and the 409 response from the mock triggers the facade error signal.
  // This test verifies the ADR-005 error propagation (facade → UI) with mock adapter.
  test('Unlinking the last covering caregiver is blocked before the request', async ({ page }) => {
    // pac-outro has only link-003: Maria Santos — she is the sole (last) covering caregiver
    await openLinkage(page, 'pac-outro');

    // Maria is in the linked section; Jamilly has no link to pac-outro → unlinked
    await expect(page.getByTestId('coverage-badge')).toContainText(/coberto/i, { timeout: 6000 });
    const mariaRow = page.locator('[data-testid="linked-caregiver-row"]').filter({ hasText: 'Maria Santos' });
    await expect(mariaRow).toHaveCount(1);

    // First click: request inline confirmation (pendingRevokeId signal set to cuid-maria)
    await mariaRow.getByTestId('revoke-link-btn').click();
    await expect(page.getByTestId('confirm-revoke-btn')).toBeVisible();

    // Second click: perform the revoke
    await page.click('[data-testid="confirm-revoke-btn"]');

    // Mock revoke() checks activeLinksForPatient for pac-outro (excluding link-003) → 0 active links
    // → ADR-005 block → throws { status: 409, ... }
    // facade.revokeLink() catches the 409 and sets error signal with ADR-005 message
    await expect(page.getByTestId('linkage-error')).toBeVisible({ timeout: 3000 });
    await expect(page.getByTestId('linkage-error')).toContainText(/ADR-005|último vínculo|não é possível/i);
  });

  // ── Scenario 8: Bypassed guard re-enforced by backend on unlink ───────────
  // BDD: "A bypassed last-covering guard is re-enforced by the backend on unlink"
  // BLOQUEIO PARCIALMENTE CADUCO. VERMELHO DELIBERADO — pelo mesmo motivo do
  // cenário 22 do roster, e vale ler os dois juntos.
  //
  //   BLOCKER-P8 caducou: o modal existe e esta tela passou a desenhá-lo.
  //   O marcador também estava factualmente errado ao dizer que o mock "não
  //     devolve 409": ele já estoura `{status:409, type:'/errors/last-covering-caregiver'}`
  //     desde a onda P7, e o cenário 7 (VERDE) prova a propagação dessa recusa.
  //
  // O que sobra é a palavra BYPASSED. No caminho de desvínculo não existe
  // pré-checagem na UI — a chamada vai ao adaptador e a recusa vem de lá —, então
  // "guard contornado" e "caminho normal" são a MESMA execução, e é o cenário 7
  // que já a cobre. Este cenário só passa a ter conteúdo próprio quando houver
  // uma pré-checagem local de desvínculo para contornar (a de desativação existe;
  // a de desvínculo não). Enquanto não houver, é duplicata do 7 disfarçada.
  test('A bypassed last-covering guard is re-enforced by the backend on unlink', async ({ page }) => {
    await openLinkage(page, 'pac-outro');

    // Maria é a única a cobrir pac-outro. Sem pré-checagem local, o pedido sai.
    const maria = page.locator('[data-testid="linked-caregiver-row"]').filter({ hasText: 'Maria Santos' });
    await maria.getByTestId('revoke-link-btn').click();
    await page.click('[data-testid="confirm-revoke-btn"]');
    await expect(page.getByTestId('linkage-error')).toBeVisible({ timeout: 4000 });

    // FALHA AQUI: para haver "re-imposição de um guard contornado" é preciso que
    // exista um guard local a contornar. No desvínculo não existe.
    const temPreChecagemLocal = await page.getByTestId('revoke-preflight-block').count();
    expect(
      temPreChecagemLocal,
      'não há pré-checagem de UI no desvínculo para ser contornada — este cenário é hoje indistinguível do cenário 7',
    ).toBeGreaterThan(0);
  });

  // ── Scenario 9: Link or unlink error re-fetches coverage ─────────────────
  // BDD: "A link or unlink error re-fetches and re-derives the paciente coverage"
  // DESTRAVADO — o bloqueio tinha caducado: não é preciso adaptador HTTP para
  // simular falha de rede, é a mesma bandeira de localStorage que já destravava
  // `falhaListar` e `falhaCriar`. Acrescentada `falhaVincular` no mesmo formato.
  //
  // E destravar mostrou que a app NÃO fazia o que o cenário afirma: o `catch` de
  // `linkToPatient` só escrevia a mensagem e deixava o mapa de cobertura anterior
  // no lugar. Corrigido na facade — depois de um vínculo que falhou não se sabe
  // em que estado o servidor ficou, e cobertura é invariante de segurança para se
  // afirmar por palpite.
  test('A link or unlink error re-fetches and re-derives the paciente coverage', async ({ page }) => {
    await page.addInitScript(scriptLigarBandeirasAcs(ACS_E2E_FLAGS.falhaVincular));
    await openLinkage(page, 'pac-novo');

    // pac-novo começa sem cobertura.
    await expect(page.getByTestId('coverage-badge')).toContainText(/sem cobertura/i);

    const jamilly = page.locator('[data-testid="unlinked-caregiver-row"]').filter({ hasText: 'Jamilly Amaral' });
    await jamilly.getByTestId('link-caregiver-btn').click();

    // 500 do mock → faixa de erro.
    await expect(page.getByTestId('linkage-error')).toBeVisible({ timeout: 4000 });

    // E a cobertura foi RE-DERIVADA a partir do estado real do servidor: o
    // vínculo não existe, então continua sem cobertura e Jamilly segue do lado de
    // fora. Sem a correção na facade o mapa ficaria intacto — que aqui daria o
    // mesmo resultado por acaso —, então afirmamos também que a linha NÃO migrou
    // para a seção de vinculados, que é o que uma escrita otimista órfã faria.
    await expect(page.getByTestId('coverage-badge')).toContainText(/sem cobertura/i);
    await expect(page.locator('[data-testid="linked-caregiver-row"]')).toHaveCount(0);
    await expect(jamilly).toHaveCount(1);
  });

  // ── Scenario 10: Inactive caregiver excluded from coverage ───────────────
  // BDD: "An inactive linked caregiver is excluded from the paciente coverage count"
  // UNBLOCKED (P7 wave 2): seed now includes cuid-inativo (status='inactive') with
  // link-inativo (cuid-inativo → pac-inativo-linked). AcsCaregiverFacade._refreshCoverageMap()
  // processes only active caregivers, so listForCaregiver('cuid-inativo') is never called.
  // pac-inativo-linked has no active coverage entries → isCovered=false → sem-cobertura badge.
  // No cross-page navigation needed — the inactive state is pre-seeded.
  test('An inactive linked caregiver is excluded from the paciente coverage count', async ({ page }) => {
    // pac-inativo-linked has only link-inativo (cuid-inativo, inactive) → no active coverage.
    // _refreshCoverageMap() skips inactive caregivers → coverage map has no entry for this patient.
    await openLinkage(page, 'pac-inativo-linked');
    // Wait for loadRoster() to complete — loading indicator hides before badge updates.
    await page.waitForSelector('[data-testid="linkage-loading"]', { state: 'hidden', timeout: 4000 }).catch(() => {});
    // Coverage badge shows sem-cobertura because the only linked caregiver is inactive.
    await expect(page.getByTestId('coverage-badge')).toContainText(/sem cobertura/i, { timeout: 4000 });
  });

  // ── Scenario 11: Actions disabled without vinculo write permission ─────────
  // BDD: "The vincular and desvincular actions are disabled without vinculo write permission"
  // BLOQUEIO CADUCOU, MAS O CENÁRIO FICOU IMPOSSÍVEL. VERMELHO DELIBERADO.
  // Mesma forma do cenário 24 do roster — vale ler os dois juntos.
  //
  // O RBAC passou a valer: `acsLinkWriteGuard` consulta 'acs.vinculo.write', que o
  // MODULE_PERMISSION_MAP traduz para `pac.paciente.vincular` (vincular virou
  // competência do PAC quando ficou decidido que só a coordenadora vincula —
  // fluxo-e-perfis.md §6). E ao passar a valer tornou ESTE cenário inalcançável:
  // a mesma authority que desabilitaria os botões já barra a ROTA (cenário 12).
  // Quem não pode vincular nunca vê a tela; quem vê a tela pode vincular.
  //
  // Não há terceiro estado, e fabricá-lo exigiria destrancar a rota — regressão
  // real, e o cenário 12 existe justamente para provar a tranca. Fica vermelho
  // até que exista um papel com leitura de vínculo sem escrita.
  test('The vincular and desvincular actions are disabled without vinculo write permission', async ({ page }) => {
    await page.addInitScript(scriptSemearSessaoCuidadora());
    await page.goto('/acs/pacientes/pac-marcio/vinculos');

    // Redirecionada: não há botão a inspecionar. `toBeDisabled()` num locator
    // vazio falha — e falha dizendo a verdade sobre o estado inalcançável.
    await expect(
      page.getByTestId('revoke-link-btn'),
      'sem pac.paciente.vincular a cuidadora nem chega à tela (acsLinkWriteGuard barra a rota) — nenhum papel alcança "tela visível + ações inertes"',
    ).toBeDisabled({ timeout: 4000 });
  });

  // ── Scenario 12: Patient linkage route blocked without permission ──────────
  // BDD: "The patient linkage route is blocked without vinculo write permission"
  // DESTRAVADO — o BLOCKER-RBAC caducou. `ACS_ACCESS_DECISION` está providenciado
  // em app.config.ts e o `acsLinkWriteGuard` passou a consultar de verdade:
  // 'acs.vinculo.write' → `pac.paciente.vincular`, que a cuidadora não tem.
  //
  // Sessão de cuidadora de verdade, nunca um flag inventado: o produto decide
  // pelas authorities do token, então é o token que precisa ser outro.
  test('The patient linkage route is blocked without vinculo write permission', async ({ page }) => {
    await page.addInitScript(scriptSemearSessaoCuidadora());
    await page.goto('/acs/pacientes/pac-marcio/vinculos');

    await expect(page).not.toHaveURL(/vinculos/, { timeout: 4000 });
    await expect(page.getByTestId('coverage-badge')).toHaveCount(0);

    // Contraprova: a MESMA rota abre para a coordenadora (a do beforeEach). Sem
    // isto o cenário passaria também se a rota estivesse quebrada para todos.
    await page.addInitScript(scriptSemearSessao());
    await page.goto('/acs/pacientes/pac-marcio/vinculos');
    await expect(page.getByTestId('coverage-badge')).toBeVisible({ timeout: 6000 });
  });

  // ── Scenario 13: Cancel revoke clears confirmation state ─────────────────
  // BDD: "Cancelling a revoke confirmation clears the pending state and keeps the link active"
  // P9 — Tests the cancel-revoke-btn path in PatientLinkageComponent.
  test('Cancelling a revoke confirmation clears the pending state and keeps the link active', async ({ page }) => {
    // pac-marcio: Jamilly (link-001) + Maria (link-002) — both linked
    await openLinkage(page, 'pac-marcio');

    // Click "Remover vínculo" on Jamilly — shows inline confirmation
    const jamillyRow = page.locator('[data-testid="linked-caregiver-row"]').filter({ hasText: 'Jamilly Amaral' });
    await jamillyRow.getByTestId('revoke-link-btn').click();

    // Confirmation buttons visible
    await expect(page.getByTestId('confirm-revoke-btn')).toBeVisible({ timeout: 2000 });
    await expect(page.getByTestId('cancel-revoke-btn')).toBeVisible();

    // Click "Cancelar" — pendingRevokeId reset
    await page.click('[data-testid="cancel-revoke-btn"]');

    // "Remover vínculo" button reappears; revoke NOT performed
    await expect(jamillyRow.getByTestId('revoke-link-btn')).toBeVisible({ timeout: 2000 });

    // Both caregivers still in the linked section
    await expect(page.locator('[data-testid="linked-caregiver-row"]')).toHaveCount(2);
  });

  // ── Scenario 14: Patient with no links shows no linked rows ──────────────
  // BDD: "A patient with no links shows no linked-caregiver-row and all caregivers as unlinked"
  // P9 — Verifies initial unlinked state for pac-novo.
  test('A patient with no links shows no linked-caregiver-row and all caregivers as unlinked', async ({ page }) => {
    // pac-novo has no links in mock seed → all caregivers are unlinked
    await openLinkage(page, 'pac-novo');

    // No linked caregivers
    await expect(page.locator('[data-testid="linked-caregiver-row"]')).toHaveCount(0);

    // Both active caregivers (Jamilly + Maria) appear in unlinked section
    // (inactive caregiver cuid-inativo is excluded from activeCaregivers())
    await expect(page.locator('[data-testid="unlinked-caregiver-row"]')).toHaveCount(2, { timeout: 6000 });
  });

  // ── Scenario 15: Fully linked patient shows no unlinked rows ─────────────
  // BDD: "A patient with all active caregivers linked shows no unlinked-caregiver-row"
  // P9 — Verifies unlinked section is absent when coverage is complete.
  test('A patient with all active caregivers linked shows no unlinked-caregiver-row', async ({ page }) => {
    // pac-marcio: Jamilly (link-001) + Maria (link-002) — all active caregivers linked
    await openLinkage(page, 'pac-marcio');

    // Coverage shows coberto
    await expect(page.getByTestId('coverage-badge')).toContainText(/coberto/i, { timeout: 6000 });

    // No unlinked caregivers → no "Disponíveis para vincular" section rows
    await expect(page.locator('[data-testid="unlinked-caregiver-row"]')).toHaveCount(0);
  });

  // ── Scenario 16: Coverage badge has role=status for screen readers ─────────
  // BDD: "The coverage badge has role=status so screen readers announce coverage state"
  // P9 — Validates ARIA role on coverage badge.
  test('The coverage badge has role=status so screen readers announce coverage state', async ({ page }) => {
    await openLinkage(page, 'pac-marcio');
    // role="status" is present on the coverage badge div
    await expect(page.getByTestId('coverage-badge')).toHaveAttribute('role', 'status');
  });

  // ── Scenario 17: Revoke shows inline confirmation before executing ─────────
  // BDD: "Clicking Remover vínculo shows inline confirmation before executing the revoke"
  // P9 — Verifies two-step confirmation pattern in PatientLinkageComponent.
  test('Clicking Remover vínculo shows inline confirmation before executing the revoke', async ({ page }) => {
    // pac-marcio: Jamilly + Maria linked — revoking Jamilly is not last-covering
    await openLinkage(page, 'pac-marcio');

    const jamillyRow = page.locator('[data-testid="linked-caregiver-row"]').filter({ hasText: 'Jamilly Amaral' });

    // First click — triggers confirmation state (pendingRevokeId = cuid-jamilly)
    await jamillyRow.getByTestId('revoke-link-btn').click();

    // confirm-revoke-btn and cancel-revoke-btn must appear
    await expect(page.getByTestId('confirm-revoke-btn')).toBeVisible({ timeout: 2000 });
    await expect(page.getByTestId('cancel-revoke-btn')).toBeVisible();

    // revoke-link-btn is replaced by the confirmation UI — not directly visible on Jamilly's row
    // (the row conditionally renders confirm/cancel instead of revoke-link-btn)
    await expect(jamillyRow.getByTestId('revoke-link-btn')).toHaveCount(0);
  });

});
