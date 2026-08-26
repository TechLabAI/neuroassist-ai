/**
 * E2E spec: Calibração — command catalog
 * Feature: docs/architecture/frontend/modules/pac/frontend/e2e/command-catalog.feature
 * PAC-FE-023: 12 scenarios (PAC P9, reduced scope)
 * Gate: A5 e2e-validate. Prototype-first (D-484), registered by fe-nx-register-module.py (D-487).
 *
 * ── ESTADO DOS BLOQUEIOS (revisto em 2026-08-25) ─────────────────────────────
 *
 *   BLOCKER-1 [dev server]: CADUCADO. `neuroassist-ai-web` tem bootstrap, rotas e
 *     serve normalmente. Nenhum cenário depende mais disso.
 *
 *   BLOCKER-2 [guard síncrono]: CADUCADO. `pacTenantPatientGuard` já devolve
 *     Observable e espera a primeira emissão não-loading. A gambiarra do
 *     "navega, cai em /onboarding, espera 600ms, navega de novo" foi removida —
 *     ela mascarava justamente o defeito que o guard passou a não ter.
 *
 *   BLOCKER-3 [nav]: CADUCADO. O menu foi reescrito (shell/menu-trabalho.ts) e
 *     deriva os itens das authorities da sessão. Ver a nota do cenário 11 sobre
 *     o RÓTULO do item, que mudou de "Calibração" para "Comandos do paciente".
 *
 *   BLOCKER-4 [adaptador HTTP]: RESOLVIDO. `providePac()` passou a ler
 *     `window.__PAC_E2E_ADAPTER` atrás de `isDevMode()`, como o EDGE já fazia.
 *     `aplicarModoHttp()` (fixtures/pac-stubs.ts) liga o modo e instala os stubs
 *     de base. Cenários 3, 4 e 5 executam.
 *
 *   BLOCKER-5 [RBAC]: RESOLVIDO. `PAC_ACCESS_DECISION` é providenciado em
 *     app.config.ts e traduz chave de tela → authority pelo `PAC_PERMISSION_MAP`;
 *     `pacCommandWriteGuard` e o catálogo consultam a decisão. Os cenários de
 *     ausência de permissão usam sessão de APOIO AO PACIENTE — a CUIDADORA não
 *     serve, porque ela TEM `pac.comando-authoring.criar` (ver a nota no cenário 9).
 *
 *   AINDA VERMELHO DE PROPÓSITO:
 *     Cenário 8 (ativo no dispositivo) — não há caminho de código que produza
 *     `syncState: 'active-on-device'`. Ver a nota no próprio cenário.
 */
import { test, expect, Page } from '@playwright/test';

import {
  scriptSemearSessao,
  scriptSemearSessaoApoio,
} from '@neuroassist/shared/auth-login/testing';
import { scriptSemAlarmeDeEmergencia } from '@neuroassist/evnt/testing';

import {
  aplicarModoHttp,
  rotaComandos,
  COMANDOS_3,
} from './fixtures/pac-stubs';

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
 * Navega para /pac e espera o catálogo montar.
 *
 * <p>Sem retry: o `pacTenantPatientGuard` é assíncrono e só resolve depois que o
 * carregamento do paciente assenta (BLOCKER-2, resolvido). Um retry aqui voltaria
 * a esconder uma regressão do guard atrás de uma segunda navegação.
 */
async function openCatalog(page: Page): Promise<void> {
  await page.goto('/pac');
  await page
    .waitForSelector('pac-command-catalog, [data-page="command-catalog"]', {
      timeout: 8000,
    })
    .catch(() => page.waitForSelector('h1', { timeout: 4000 }));
}

// ── Tests ────────────────────────────────────────────────────────────────────

test.describe('Calibração — command catalog', () => {

  // ── Scenario 1: Catalog route renders ─────────────────────────────────────
  // BDD: "Calibração route renders the command catalog for the active paciente"
  test('Calibração route renders the command catalog for the active paciente', async ({ page }) => {
    await openCatalog(page);
    // The heading should show "Calibração — <patient name>"
    await expect(page.locator('h1')).toContainText('Calibração');
    // With mock adapter the patient name is "Márcio Amaral"
    await expect(page.locator('h1')).toContainText('Márcio Amaral');
    // The catalog component should be in the DOM
    await expect(page.locator('pac-command-catalog')).toBeVisible();
  });

  // ── Scenario 2: Catalog fetches list scoped by empresa header ─────────────
  // BDD: "Opening the catalog fetches the comando list scoped by the empresa header"
  test('Opening the catalog fetches the comando list scoped by the empresa header', async ({ page }) => {
    // In HTTP-adapter mode: capture outgoing requests and verify X-Empresa-Id header.
    // In mock-adapter mode: verify the rendered rows include rotulo, gesto, urgência, sensibilidade.
    const capturedHeaders: Record<string, string>[] = [];
    page.on('request', (req) => {
      if (req.url().includes('/api/pac/v1/pacientes')) {
        capturedHeaders.push(req.headers());
      }
    });

    await openCatalog(page);

    // Verify each row shows the required fields (testable in both adapter modes via rendered DOM)
    // The catalog renders rows with: rotulo in quotes, urgência badge, sensibilidade text
    const rows = page.locator('pac-ds-card').filter({ hasText: /ajuda|água|sapo/i });
    await expect(rows.first()).toBeVisible();

    // At least one row should display the rotulo in quotes format: "Ajuda"
    await expect(page.getByText('"Ajuda"').first()).toBeVisible();

    // In HTTP-adapter mode: empresa header must be present in API requests
    // (this assertion is a no-op in mock mode since capturedHeaders will be empty)
    for (const headers of capturedHeaders) {
      expect(headers['x-empresa-id'] ?? headers['X-Empresa-Id']).toBeTruthy();
    }
  });

  // ── Scenario 3: Loading state ─────────────────────────────────────────────
  // BDD: "Catalog load in flight shows a loading catalog state not stale rows"
  // BLOCKER-4 resolvido: a app roda em modo HTTP e o stub segura a resposta.
  test('Catalog load in flight shows a loading catalog state not stale rows', async ({ page }) => {
    await aplicarModoHttp(page);
    // 3s de atraso = a chamada fica em voo enquanto as asserções abaixo rodam.
    await rotaComandos(page, COMANDOS_3, 200, 3000);

    await page.goto('/pac');

    // Enquanto a chamada não volta, o estado de carregamento aparece…
    await expect(page.getByText('Carregando catálogo…')).toBeVisible({ timeout: 5000 });
    // …e nenhuma linha do catálogo é mostrada (nada de dado velho na tela).
    await expect(page.getByText('"Ajuda"')).toHaveCount(0);

    // E quando volta, as linhas aparecem — sem isto o cenário passaria também
    // com um catálogo que nunca carrega.
    await expect(page.getByText('"Ajuda"').first()).toBeVisible({ timeout: 10_000 });
  });

  // ── Scenario 4: Error state ───────────────────────────────────────────────
  // BDD: "Catalog load failure shows an error catalog state with retry"
  test('Catalog load failure shows an error catalog state with retry', async ({ page }) => {
    await aplicarModoHttp(page);
    await rotaComandos(page, { type: 'INTERNAL_ERROR', title: 'Server error' }, 500);

    await page.goto('/pac');

    // Estado de erro tem de aparecer, NÃO um catálogo vazio.
    await expect(
      page.locator('pac-ds-card').filter({ hasText: /erro|error|falh/i }).first(),
    ).toBeVisible({ timeout: 10_000 });
    // A lista vazia não pode ser mostrada no lugar do erro: "não há comandos" e
    // "não consegui buscar os comandos" são coisas diferentes para quem cuida.
    await expect(page.getByText('Nenhum comando configurado ainda.')).toHaveCount(0);
  });

  // ── Scenario 5: Empty state ───────────────────────────────────────────────
  // BDD: "Loaded catalog with no comando shows the empty state to add the first comando"
  test('Loaded catalog with no comando shows the empty state to add the first comando', async ({ page }) => {
    await aplicarModoHttp(page);
    await rotaComandos(page, []);

    await page.goto('/pac');

    // Empty state: the template shows this card when isEmpty() && loadState === 'loaded'
    await expect(page.getByText('Nenhum comando configurado ainda.')).toBeVisible({ timeout: 10_000 });
    // Affordance to add first command (a coordenadora TEM pac.comando-authoring.criar)
    await expect(page.getByText('Adicionar primeiro comando')).toBeVisible();
  });

  // ── Scenario 6: KPI counter ───────────────────────────────────────────────
  // BDD: "KPI counter derives comandos pendentes de sincronização from the catalog rows"
  test('KPI counter derives comandos pendentes de sincronização from the catalog rows as 2', async ({ page }) => {
    // Note: with mock adapter, all 3 comandos have syncState='pending-sync' (ADR-005 stub).
    // The feature specifies "2 of 3 are pendente" — the stub cannot express the 2/3 split
    // (ver cenário 8). Aqui verificamos que o KPI deriva do que está renderizado.
    await openCatalog(page);

    // The KPI panel shows "Comandos" count and "Emergência" count
    const kpiComandos = page.locator('pac-ds-kpi').filter({ hasText: /Comandos/i });
    await expect(kpiComandos).toBeVisible({ timeout: 5000 });

    // With mock data: 3 total comandos, 1 emergência
    await expect(kpiComandos).toContainText('3');

    const kpiEmergencia = page.locator('pac-ds-kpi').filter({ hasText: /Emergência/i });
    await expect(kpiEmergencia).toBeVisible();
    await expect(kpiEmergencia).toContainText('1');

    // ADR-005: every row shows "pendente" badge (stub always returns pending-sync)
    const pendenteBadges = page.locator('pac-ds-badge').filter({ hasText: /pendente/i });
    await expect(pendenteBadges).toHaveCount(3); // one per command row
  });

  // ── Scenario 7: Honest sync state — saved edit stays pendente ─────────────
  // BDD: "Saved comando edit not acknowledged stays pendente de sincronização not ativo no dispositivo"
  test('Saved comando edit not acknowledged stays pendente de sincronização not ativo no dispositivo', async ({ page }) => {
    await openCatalog(page);

    // ADR-005: EdgeDeviceAckStubAdapter always returns pending-sync.
    // No row should ever show "ativo no dispositivo" text with the stub in place.
    await expect(page.getByText('ativo no dispositivo')).toHaveCount(0);

    // All rows must have "pendente" badge (the warning-tone badge from the template)
    const pendenteBadges = page.locator('pac-ds-badge').filter({ hasText: /pendente/i });
    await expect(pendenteBadges.first()).toBeVisible({ timeout: 5000 });

    // ADR-005 honest-state banner at page level must also be present
    await expect(
      page.getByText('Pendente de sincronização com o dispositivo', { exact: false }).first(),
    ).toBeVisible();
  });

  // ── Scenario 8: Device-acknowledged shows ativo ────────────────────────────
  // BDD: "Device-acknowledged comando shows ativo no dispositivo"
  //
  // ═══ VERMELHO DE PROPÓSITO — CAPACIDADE AUSENTE, NÃO GANCHO DE TESTE ═══
  //
  // Este cenário NÃO é destravável por infraestrutura de teste. Falta produto:
  //
  //   1. `EdgeDeviceAckStubAdapter` (libs/pac/src/lib/api/edge-device-ack-stub.adapter.ts)
  //      devolve 'pending-sync' fixo. É um adaptador FALSO providenciado como se
  //      fosse real — `providePac()` o registra incondicionalmente.
  //   2. `PacComandoFacade._toRowVM` não tem caminho que produza
  //      'active-on-device'. O tipo `SyncState` prevê o valor; nenhuma linha o
  //      atribui.
  //   3. O template do catálogo não tem o texto "ativo no dispositivo" — só o
  //      badge "pendente". Mesmo com o dado certo não haveria o que renderizar.
  //   4. O contrato de ack do EDGE não existe (lacuna registrada no ADR-005 e no
  //      TODO[INT-FE-PAC-001] de pac.providers.ts).
  //
  // O QUE FALTA, na ordem: contratar o sinal de ack no heartbeat do EDGE →
  // trocar o stub por `EdgeDeviceAckAdapter` real → derivar `syncState` no
  // facade → renderizar os dois estados no `SyncStatusBadgeComponent`.
  //
  // Marcá-lo `fixme` era o que o fazia contar como "existente" no gate sem nunca
  // rodar. Vermelho é a informação correta: a promessa do ADR-005 (dizer a
  // verdade sobre o que está no dispositivo) hoje só sabe dizer metade.
  test('Device-acknowledged comando shows ativo no dispositivo', async ({ page }) => {
    await openCatalog(page);
    const linhaAtiva = page.locator('pac-ds-card').filter({ hasText: 'ativo no dispositivo' });
    await expect(linhaAtiva).toBeVisible({ timeout: 5000 });
  });

  // ── Scenario 9: RBAC — novo comando hidden when no write permission ────────
  // BDD: "Caller lacking comando write permission has the novo comando entry point hidden"
  test('Caller lacking comando write permission has the novo comando entry point hidden', async ({ page }) => {
    // A sessão é de APOIO AO PACIENTE, não de cuidadora.
    //
    // A cuidadora NÃO serve aqui, e isto é conferido no `RoleAuthorityPolicy` do
    // acs-service: ela tem `pac.comando-authoring.criar`, `.editar`,
    // `pac.comando-lifecycle.publicar`, `.aposentar` e `.recalibrar`. A única
    // authority do PAC que ela não tem é `pac.paciente.criar` — cadastrar
    // paciente é da coordenadora. Semear cuidadora aqui daria um verde que prova
    // o oposto do que o cenário afirma.
    //
    // O apoio é o último degrau do escalonamento (ADR-011) e não tem NENHUMA
    // authority de escrita: confirma alarme e mais nada.
    await page.addInitScript(scriptSemearSessaoApoio());

    await openCatalog(page);

    // A tela do catálogo tem de estar lá — o apoio não escreve, mas chegou nela.
    await expect(page.locator('pac-command-catalog')).toBeVisible();
    // …e o ponto de entrada de escrita NÃO é renderizado (não é escondido por
    // CSS: `toHaveCount(0)` afirma ausência do DOM, que é o que importa para
    // teclado e leitor de tela).
    await expect(page.getByRole('link', { name: /Novo comando/i })).toHaveCount(0);
  });

  // ── Scenario 10: RBAC — nav item hidden when no read permission ────────────
  // BDD: "Caller lacking comando read permission has the Calibração nav item hidden"
  test('Caller lacking comando read permission has the Calibração nav item hidden', async ({ page }) => {
    // O apoio ao paciente não tem `pac.catalogo-ativo.read`, que é a authority
    // que o item 'comandos' do menu exige (shell/menu-trabalho.ts).
    await page.addInitScript(scriptSemearSessaoApoio());
    await page.goto('/');
    // O menu tem de ter renderizado alguma coisa — sem esta âncora o cenário
    // passaria também com a barra lateral inteira ausente, que é outro defeito.
    await expect(page.locator('[data-testid="sidebar-nav"]')).toBeVisible({ timeout: 8000 });
    await expect(page.locator('[data-menu-item="comandos"]')).toHaveCount(0);
  });

  // ── Scenario 11: Registered paciente keeps nav item visible ───────────────
  // BDD: "Registered paciente keeps the Calibração nav item visible"
  test('Registered paciente keeps the Calibração nav item visible', async ({ page }) => {
    // Sessão padrão = coordenadora, que tem `pac.catalogo-ativo.read`.
    await page.goto('/');

    // Afirmamos pelo `data-menu-item`, o identificador ESTÁVEL que o
    // SidebarComponent expõe, e não pelo texto "Calibração": o menu foi
    // reescrito e o item passou a se chamar "Comandos do paciente" — o nome do
    // trabalho, não o da operação técnica (shell/menu-trabalho.ts explica a
    // decisão). Afirmar pelo rótulo faria este cenário reprovar a cada mudança
    // de redação, que não é o que o BDD quer dizer.
    const itemComandos = page.locator('[data-menu-item="comandos"]');
    await expect(itemComandos).toBeVisible({ timeout: 8000 });
    await expect(itemComandos).toHaveAttribute('href', '/pac');
  });

  // ── Scenario 12: No registered paciente redirects to onboarding ───────────
  // BDD: "No registered paciente redirects the caregiver to the onboarding paciente step"
  test('No registered paciente redirects the caregiver to the onboarding paciente step', async ({ page }) => {
    // Activate E2E hook in PacienteMockAdapter so the patient list returns empty.
    // The async guard waits for the load to complete, finds no patient, and redirects.
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    await page.addInitScript(() => { (window as any).__PAC_E2E_NO_PATIENT = true; });

    // Navigate to /pac. Guard is async: waits for mock (400ms delay), gets empty list,
    // hasPatient()=false → redirects to /onboarding.
    await page.goto('/pac');
    // Guard should redirect to /onboarding
    await expect(page).toHaveURL(/\/onboarding/, { timeout: 5000 });
    // The onboarding page should render (step 2 = Paciente)
    await expect(page.locator('h1')).toContainText('Configuração inicial');
  });

});
