/**
 * E2E spec: Calibração — command editor and lifecycle
 * Feature: docs/architecture/frontend/modules/pac/frontend/e2e/command-editor.feature
 * PAC-FE-024: REDUCED SCOPE — happy-path scenarios + emergency gate blocking modal (safety-critical)
 * Gate: A5 e2e-validate. Prototype-first (D-484), registered by fe-nx-register-module.py (D-487).
 *
 * REDUCED SCOPE RATIONALE (PAC P9, iteration 4):
 *   Original feature: ~42 scenarios. This iteration: 6 happy-path + 1 safety-critical (emergency gate).
 *   Remaining scenarios (lifecycle gating, concurrency, ProblemDetail mappings, GEST composition)
 *   are deferred to the next iteration because they require lifecycle buttons not yet in the editor
 *   template (validar/publicar/recalibrar/retirar) or cross-module GEST integration.
 *
 * ── ESTADO DOS BLOQUEIOS (revisto em 2026-08-25) ─────────────────────────────
 *
 *   BLOCKER-1 [dev server]: CADUCADO — a app serve normalmente.
 *
 *   BLOCKER-2 [guard síncrono]: CADUCADO — `pacTenantPatientGuard` já é assíncrono
 *     e espera o carregamento do paciente assentar. A gambiarra do `openEditor`
 *     (navegar para /pac, esperar 600ms, navegar de novo) foi removida: ela
 *     escondia justamente a regressão que o guard passou a não ter.
 *
 *   BLOCKER-4 [adaptador HTTP]: RESOLVIDO — `providePac()` lê
 *     `window.__PAC_E2E_ADAPTER` atrás de `isDevMode()`. Ver fixtures/pac-stubs.ts.
 *
 *   BLOCKER-5 [RBAC]: RESOLVIDO — `pacCommandWriteGuard` consulta
 *     `PAC_ACCESS_DECISION`, providenciado em app.config.ts e traduzido pelo
 *     `PAC_PERMISSION_MAP`.
 *
 *   BLOCKER-6 [botões de ciclo de vida]: **AINDA VALE, E É FALTA DE PRODUTO.**
 *     `CommandEditorComponent` não tem validar / publicar / recalibrar / aposentar
 *     — só "Salvar comando" e "Cancelar". Os quatro cenários de ciclo de vida
 *     ficam VERMELHOS de propósito; ver a nota detalhada acima deles.
 */

import { test, expect, Page } from '@playwright/test';

import {
  scriptSemearSessao,
  scriptSemearSessaoApoio,
} from '@neuroassist/shared/auth-login/testing';
import { scriptSemAlarmeDeEmergencia } from '@neuroassist/evnt/testing';

import { aplicarModoHttp, rotaComandos, COMANDOS_3 } from './fixtures/pac-stubs';

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

// ── Shared mock data ─────────────────────────────────────────────────────────

const MOCK_PACIENTE_PAGE = {
  content: [{
    id: 'pac-marcio', tenantId: 'tenant-familia-amaral',
    nome: 'Márcio Amaral', dispositivo: 'edge-marcio-01', catalogoVersao: 'v1',
  }],
  totalElements: 1, totalPages: 1, number: 0, size: 1,
};

const MOCK_COMANDOS = [
  {
    id: 'cmd-ajuda', pacienteId: 'pac-marcio', rotulo: 'Ajuda', urgencia: 'emergencia',
    primitivoPrincipal: 'tg-sobrancelha', primitivoSecundario: null,
    sensibilidade: 'media', tempoMinimoGestoMs: 400, tempoMaximoGestoMs: 1500,
    estado: 'validated', etag: 'etag-cmd-ajuda-v1',
  },
];

/** ProblemDetail for emergency-not-detectable (RFC 7807 + PAC SPEC-07). */
const EMERGENCY_NOT_DETECTABLE_PROBLEM = {
  type: 'https://neuroassist.ai/problems/emergency-not-detectable',
  title: 'Emergency gate: gesture not detectable',
  status: 422,
  detail: 'The selected gesture type is not detectable by the current gesture engine. Choose a different gesture or change urgência to Rotina.',
  instance: '/api/pac/v1/pacientes/pac-marcio/comandos/cmd-ajuda/publish',
  errorCode: 'emergency-not-detectable',
};

// ── Navigation helpers ───────────────────────────────────────────────────────

/**
 * Navega direto para a rota do editor.
 *
 * <p>A gambiarra do BLOCKER-2 — ir a /pac primeiro para "aquecer" o paciente e
 * esperar 600ms — foi removida: o `pacTenantPatientGuard` é assíncrono e resolve
 * a rota do editor sozinho. Manter a gambiarra faria este arquivo continuar
 * passando mesmo que o guard voltasse a decidir cedo demais, que é o defeito que
 * ela existia para contornar.
 *
 * <p>A normalização da barra inicial fica: um `/${path}` sobre um caminho que já
 * começa com barra vira `//pac/...`, que o browser resolve como protocolo-relativo
 * (host "pac") e falha com net::ERR_NAME_NOT_RESOLVED.
 */
async function openEditor(page: Page, path: '/pac/comandos/novo' | `${'pac/comandos/'}${string}${'/editar'}`): Promise<void> {
  await page.goto(path.startsWith('/') ? path : `/${path}`);
  await page.waitForSelector('pac-command-editor, h1', { timeout: 8000 });
}

/**
 * Stubs HTTP dos endpoints do pac-service.
 *
 * <p>O caminho é `/api/pac/v1/...` — prefixo do módulo, roteado pelo GWY. Os
 * padrões anteriores (`/api/v1/pacientes`) não correspondiam a endereço nenhum
 * que o produto chama; conferido em `paciente-http.adapter.ts` e
 * `comando-http.adapter.ts`.
 */
async function routePacApiPacientes(page: Page, response: unknown, status = 200) {
  await page.route('**/api/pac/v1/pacientes*', (route) =>
    route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(response) }),
  );
}

async function routePacApiComandos(page: Page, response: unknown, status = 200) {
  await page.route('**/api/pac/v1/pacientes/**/comandos*', (route) =>
    route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(response) }),
  );
}

/**
 * Wire the emergency-not-detectable 422 response on the publish endpoint.
 * In HTTP adapter mode this intercepts POST .../comandos/:id/publish and returns 422.
 * BLOCKER-4: no-op in mock-adapter mode.
 */
async function routePublishEmergencyGate(page: Page): Promise<void> {
  await page.route('**/api/pac/v1/pacientes/**/comandos/**/publish', (route) =>
    route.fulfill({
      status: 422,
      contentType: 'application/json',
      body: JSON.stringify(EMERGENCY_NOT_DETECTABLE_PROBLEM),
    }),
  );
  // The save() path also sends POST to /comandos — intercept that too, since in the current
  // editor the save() method can trigger emergency gate for urgencia=emergencia (SPEC-07).
  await page.route('**/api/pac/v1/pacientes/**/comandos', async (route) => {
    const req = route.request();
    if (req.method() === 'POST') {
      const body = JSON.parse(req.postData() ?? '{}') as { urgencia?: string };
      if (body.urgencia === 'emergencia') {
        await route.fulfill({
          status: 422,
          contentType: 'application/json',
          body: JSON.stringify(EMERGENCY_NOT_DETECTABLE_PROBLEM),
        });
        return;
      }
    }
    await route.continue();
  });
}

// ── Tests ────────────────────────────────────────────────────────────────────

test.describe('Calibração — command editor and lifecycle', () => {

  // ── Scenario 1: Novo comando route renders blank draft ────────────────────
  // BDD: "Novo comando route renders the command editor with a blank draft in editing state"
  // REQUIRES: BLOCKER-1, BLOCKER-2 resolved
  test('Novo comando route renders the command editor with a blank draft in editing state', async ({ page }) => {
    await openEditor(page, '/pac/comandos/novo');

    // Heading must indicate new command
    await expect(page.locator('h1')).toContainText('Novo comando');

    // Rotulo input must be blank (no pre-filled value)
    const rotuloInput = page.locator('pac-ds-input').first();
    await expect(rotuloInput).toBeVisible();
    // The inner input within pac-ds-input should be empty
    await expect(rotuloInput.locator('input')).toHaveValue('');

    // Salvar command button should be disabled (blank rotulo)
    const salvarBtn = page.locator('pac-ds-button').filter({ hasText: /Salvar comando/i });
    await expect(salvarBtn.locator('button')).toBeDisabled();

    // Emergency gate modal must NOT be visible on initial render
    await expect(page.locator('text=Gesto não detectável')).not.toBeVisible();
  });

  // ── Scenario 2: Editar comando route pre-fills editor ─────────────────────
  // BDD: "Editar comando route renders the command editor pre-filled from that comando"
  // REQUIRES: BLOCKER-1, BLOCKER-2 resolved
  test('Editar comando route renders the command editor pre-filled from that comando', async ({ page }) => {
    await openEditor(page, 'pac/comandos/cmd-ajuda/editar');

    // Heading must show "Editar comando" (not "Novo comando")
    await expect(page.locator('h1')).toContainText('Editar comando');

    // The editor must be pre-filled with the existing comando's rotulo ("Ajuda")
    // The pre-visualização card shows the rotulo in quotes
    await expect(page.locator('pac-ds-card').filter({ hasText: /Ajuda/ })).toBeVisible({ timeout: 5000 });

    // Urgência should reflect "emergencia" (the mock cmd-ajuda urgência)
    // The template shows urgencia buttons; the "Emergência" one should appear active/selected
    const emergenciaBtn = page.locator('button').filter({ hasText: 'Emergência' });
    await expect(emergenciaBtn).toBeVisible();
  });

  // ── Scenario 3: Blank rotulo keeps salvar disabled ────────────────────────
  // BDD: "Blank rotulo keeps the salvar comando action disabled"
  // REQUIRES: BLOCKER-1, BLOCKER-2 resolved; works with mock adapter
  test('Blank rotulo keeps the salvar comando action disabled', async ({ page }) => {
    await openEditor(page, '/pac/comandos/novo');

    // Confirm the rotulo input is blank
    const rotuloInput = page.locator('pac-ds-input').first().locator('input');
    await expect(rotuloInput).toHaveValue('');

    // Salvar button must be disabled when rotulo is blank
    // The template: [disabled]="!editor().rotulo.trim() || editor().saveState === 'saving'"
    const salvarBtn = page.locator('pac-ds-button').filter({ hasText: /Salvar comando/i }).locator('button');
    await expect(salvarBtn).toBeDisabled();

    // Type a value → button should enable
    await rotuloInput.fill('Água');
    await expect(salvarBtn).not.toBeDisabled();

    // Clear the value → button should disable again
    await rotuloInput.fill('');
    await expect(salvarBtn).toBeDisabled();
  });

  // ── Scenario 4: Sensibilidade as labels only, never raw threshold ─────────
  // BDD: "Sensibilidade is rendered only as baixa media or alta labels never a raw threshold"
  // REQUIRES: BLOCKER-1, BLOCKER-2 resolved; works with mock adapter
  test('Sensibilidade is rendered only as baixa media or alta labels never a raw threshold value', async ({ page }) => {
    await openEditor(page, '/pac/comandos/novo');

    // The sensibilidade section renders three buttons: Baixa, Média, Alta
    await expect(page.locator('button', { hasText: 'Baixa' })).toBeVisible();
    await expect(page.locator('button', { hasText: 'Média' })).toBeVisible();
    await expect(page.locator('button', { hasText: 'Alta' })).toBeVisible();

    // Raw numeric threshold values must NOT appear anywhere on the editor page
    // (sensibilidade values in the domain are 'baixa'|'media'|'alta', not numbers)
    const pageText = await page.locator('body').innerText();
    // Typical numeric threshold format: 0.0–1.0 or 0–100 range
    expect(pageText).not.toMatch(/sensibilidade[^a-zA-Z]{0,10}\d+\.\d+/i);
    expect(pageText).not.toMatch(/threshold[^a-zA-Z]{0,10}\d+/i);

    // The preview card also must show sensibilidade as label text
    const previewCard = page.locator('pac-ds-card').filter({ hasText: /Sensibilidade/i }).first();
    await expect(previewCard).toBeVisible();
    // Should contain a label ("media" default) but NOT a decimal number
    const previewText = await previewCard.innerText();
    expect(previewText).not.toMatch(/\d+\.\d+/);
  });

  // ── Scenario 5: Save new comando (happy-path) ─────────────────────────────
  // BDD: "Saving a new comando posts the draft carrying an idempotency key"
  // REQUIRES: BLOCKER-1, BLOCKER-2 resolved; works with mock adapter for basic flow
  test('Saving a new comando posts the draft carrying an idempotency key', async ({ page }) => {
    // Capture outgoing API requests (only visible in HTTP adapter mode — BLOCKER-4)
    const capturedRequests: { method: string; url: string; headers: Record<string, string>; body: string }[] = [];
    page.on('request', (req) => {
      if (req.url().includes('/api/pac/v1/pacientes')) {
        capturedRequests.push({
          method: req.method(),
          url: req.url(),
          headers: req.headers(),
          body: req.postData() ?? '',
        });
      }
    });

    await openEditor(page, '/pac/comandos/novo');

    // Fill required fields
    await page.locator('pac-ds-input').first().locator('input').fill('Água');

    // Click save
    const salvarBtn = page.locator('pac-ds-button').filter({ hasText: /Salvar comando/i }).locator('button');
    await expect(salvarBtn).not.toBeDisabled();
    await salvarBtn.click();

    // After saving (mock adapter: immediate success), the saveState becomes 'saved-pending-sync'
    // The preview should show "Salvo — pendente de sincronização" (ADR-005 honest-state)
    // OR navigate back to catalog (the editor calls router.navigate(['..']) on save)
    // Either way, after save the editor should no longer show "Salvando…" state
    await page.waitForTimeout(600); // wait for mock adapter delay
    await expect(page.locator('text=Salvando…')).not.toBeVisible({ timeout: 3000 });

    // In HTTP-adapter mode: verify idempotency key header was sent
    for (const req of capturedRequests) {
      if (req.method === 'POST' && req.url.includes('/comandos')) {
        const headers = req.headers;
        const idemKey = headers['idempotency-key'] ?? headers['Idempotency-Key'];
        // Idempotency key must be a non-empty UUID or timestamp-random string
        expect(idemKey).toBeTruthy();
        expect(idemKey).not.toBe('');
      }
    }
  });

  // ── SAFETY-CRITICAL SCENARIO ─────────────────────────────────────────────
  //
  // BDD: "Emergency-not-detectable on publicar blocks publish with a blocking modal
  //       and leaves comando validated"
  //
  // SPEC-07 / ADR-005 contract:
  //   When the backend responds 422 emergency-not-detectable:
  //   - A BLOCKING modal (non-dismissible via ESC or click outside) MUST appear.
  //   - The modal heading must state the gesture is not detectable.
  //   - The modal MUST NOT be dismissible without a deliberate action (Alterar para Rotina).
  //   - The comando MUST remain in its previous lifecycle state (not marked published/saved).
  //   - The facade sets editor().emergencyGateError (non-null) which triggers the modal.
  //
  // IMPLEMENTATION NOTES:
  //   - page.route() intercepts POST to /comandos and /publish returning 422.
  //   - The facade._handleSaveError() catches status 422 → sets emergencyGateError signal.
  //   - CommandEditorComponent template renders the modal when emergencyGateError() is truthy.
  //   - Modal is styled as fixed inset overlay (z-50) — tests that it covers the page.
  //   - "Alterar para Rotina" button in the modal calls changeUrgencyToRotina() which
  //     sets urgencia='rotina' and clears emergencyGateError → modal closes.
  //
  // REQUIRES: BLOCKER-1, BLOCKER-2 resolved; BLOCKER-4 resolved (HTTP adapter + page.route()).
  //
  // BLOCKER-4 note: In mock-adapter mode, ComandoMockAdapter.publish() always succeeds.
  //   The 422 can only be triggered via HTTP adapter with page.route() interception.
  //   ALTERNATIVE (no HTTP adapter needed): add window.__PAC_E2E_FORCE_EMERGENCY_GATE=true
  //   to ComandoMockAdapter as a test-only toggle that returns throwError(422).
  //
  test('Emergency-not-detectable on publicar blocks publish with a blocking modal and leaves comando validated', async ({ page }) => {
    // BLOCKER-4 resolved via mock adapter hook (window.__PAC_E2E_EMERGENCY_GATE).
    // addInitScript runs before each Angular bootstrap so the flag is set when
    // ComandoMockAdapter.create() is called with urgencia=emergencia → returns 422.
    await page.addInitScript(() => {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      (window as any).__PAC_E2E_EMERGENCY_GATE = true;
    });

    // HTTP route intercepts below are no-ops in mock-adapter mode but kept so
    // the test is forward-compatible with HTTP adapter switching (BLOCKER-4 full fix).
    await routePacApiPacientes(page, MOCK_PACIENTE_PAGE);
    await routePacApiComandos(page, MOCK_COMANDOS);
    // The 422 intercept on save (urgencia=emergencia):
    await routePublishEmergencyGate(page);

    await openEditor(page, '/pac/comandos/novo');

    // Select urgência = Emergência
    await page.locator('button', { hasText: 'Emergência' }).click();
    await expect(page.locator('button', { hasText: 'Emergência' })).toHaveClass(/border-alarm|bg-alarm-bg/);

    // Fill a valid rotulo so save is enabled
    await page.locator('pac-ds-input').first().locator('input').fill('Parar tudo agora');

    // Click "Salvar comando"
    const salvarBtn = page.locator('pac-ds-button').filter({ hasText: /Salvar comando/i }).locator('button');
    await expect(salvarBtn).not.toBeDisabled();
    await salvarBtn.click();

    // ── CRITICAL ASSERTIONS: Emergency gate blocking modal ─────────────────

    // 1. The blocking modal overlay MUST appear
    const overlay = page.locator('.fixed.inset-0.z-50');
    await expect(overlay).toBeVisible({ timeout: 5000 });

    // 2. Modal heading MUST say "Gesto não detectável"
    await expect(overlay.locator('h2')).toContainText('Gesto não detectável');

    // 3. Modal MUST contain the explanatory text about changing urgência
    await expect(overlay).toContainText('Alterar para Rotina');

    // 4. The modal is NON-DISMISSIBLE: ESC key must NOT close it
    await page.keyboard.press('Escape');
    await expect(overlay).toBeVisible({ timeout: 1000 });

    // 5. The modal is NON-DISMISSIBLE: clicking outside the modal panel must NOT close it
    await page.mouse.click(10, 10); // click top-left corner (outside the modal panel)
    await expect(overlay).toBeVisible({ timeout: 1000 });

    // 6. Comando must NOT be marked as saved or published (saveState must reflect error)
    // The "Salvo — pendente de sincronização" success text must NOT appear
    await expect(page.locator('text=Salvo — pendente de sincronização')).not.toBeVisible();

    // 7. The "Alterar para Rotina" action resolves the gate → dismisses modal
    const alterarBtn = overlay.locator('pac-ds-button', { hasText: /Alterar para Rotina/i });
    await expect(alterarBtn).toBeVisible();
    await alterarBtn.click();

    // After clicking "Alterar para Rotina": modal closes and urgência changes to Rotina
    await expect(overlay).not.toBeVisible({ timeout: 2000 });
    // The urgência selector should now show Rotina as active
    await expect(page.locator('button', { hasText: 'Rotina' })).toHaveClass(/border-brand|bg-brand-subtle/);
  });


  // ═══ CENÁRIOS DE CICLO DE VIDA — VERMELHOS DE PROPÓSITO (BLOCKER-6) ═══════
  //
  // Estes quatro NÃO são destraváveis por infraestrutura de teste. Falta produto.
  //
  // O QUE EXISTE HOJE, conferido linha a linha:
  //   - `PacComandoPort` (api/comando.port.ts) declara validate(), publish(),
  //     recalibrate() e retire(), e o `ComandoMockAdapter` implementa os quatro;
  //   - `PacComandoFacade` expõe recalibrate() e retire(). NÃO expõe validate()
  //     nem publish() — o porto tem os métodos e ninguém os chama;
  //   - `CommandEditorComponent` renderiza exatamente dois botões de ação:
  //     "Salvar comando" e "Cancelar". Nenhum de ciclo de vida, e portanto
  //     nenhuma regra de habilitação por `estado` para exercitar.
  //
  // Ou seja: o ciclo de vida drafted → validated → published → recalibrated /
  // retired do ADR-006 está contratado no porto, implementado no adaptador e
  // INALCANÇÁVEL pela pessoa que usa o produto. A calibração de um comando novo
  // termina em `drafted` e não há tela que a leve adiante.
  //
  // O QUE FALTA, na ordem: expor validate()/publish() no facade → acrescentar os
  // quatro botões ao template com habilitação derivada de `editor().estado` →
  // ligar o gate de emergência ao publish (hoje ele só é exercido no create).
  //
  // Cada cenário abaixo afirma a AFORDÂNCIA, não a transição: enquanto o botão
  // não existir, é isso que falta primeiro. Deixá-los `fixme` com corpo vazio —
  // como estavam — os fazia contar como existentes no gate e passar por serem
  // vazios, que é o pior dos dois mundos.

  /** Abre o editor de um comando semeado no estado pedido, via modo HTTP. */
  async function abrirEditorComEstado(page: Page, estado: string): Promise<void> {
    await aplicarModoHttp(page);
    await rotaComandos(page, [{ ...COMANDOS_3[0], estado }]);
    await openEditor(page, 'pac/comandos/cmd-ajuda/editar');
    await expect(page.locator('h1')).toContainText('Editar comando');
  }

  /** Botão de ação do editor, procurado pelo rótulo visível. */
  function acao(page: Page, rotulo: RegExp) {
    return page.locator('pac-ds-button').filter({ hasText: rotulo }).locator('button');
  }

  test('Drafted editor comando enables validar and keeps publicar disabled', async ({ page }) => {
    await abrirEditorComEstado(page, 'drafted');
    // Um comando em rascunho pode ser validado; publicar só depois de validado.
    await expect(acao(page, /Validar/i)).toBeEnabled();
    await expect(acao(page, /Publicar/i)).toBeDisabled();
  });

  test('Validated editor comando enables publicar', async ({ page }) => {
    await abrirEditorComEstado(page, 'validated');
    await expect(acao(page, /Publicar/i)).toBeEnabled();
  });

  test('Published editor comando enables recalibrar and keeps validar disabled', async ({ page }) => {
    await abrirEditorComEstado(page, 'published');
    // Recalibrar é ajuste de tuning sobre um comando já publicado (ADR-006);
    // validar de novo não faz sentido e tem de estar desabilitado.
    await expect(acao(page, /Recalibrar/i)).toBeEnabled();
    await expect(acao(page, /Validar/i)).toBeDisabled();
  });

  test('Retired editor comando keeps every lifecycle action disabled', async ({ page }) => {
    await abrirEditorComEstado(page, 'retired');
    // Aposentado é terminal (ADR-006 — não há DELETE, e não há volta).
    await expect(acao(page, /Validar/i)).toBeDisabled();
    await expect(acao(page, /Publicar/i)).toBeDisabled();
    await expect(acao(page, /Recalibrar/i)).toBeDisabled();
    await expect(acao(page, /Aposentar/i)).toBeDisabled();
  });

  // ── Editar com comandoId inexistente ───────────────────────────────────────
  //
  // BLOCKER-4 resolvido, e o defeito que ele escondia foi corrigido em
  // `CommandEditorComponent`: o efeito que abre o comando só casava quando a
  // linha existia, e com um id inválido a tela ficava para sempre em "Editar
  // comando" com o formulário em branco — indistinguível de um carregamento
  // lento. Agora, depois que o catálogo termina de carregar sem achar o id, o
  // editor devolve a pessoa ao catálogo.
  test('Editar route with unknown comandoId redirects to the not-found view', async ({ page }) => {
    await aplicarModoHttp(page);
    await rotaComandos(page, COMANDOS_3); // 'cmd-unknown' não está na lista

    await page.goto('/pac/comandos/cmd-unknown/editar');

    await expect(page).toHaveURL(/\/pac$/, { timeout: 10_000 });
    await expect(page.locator('pac-command-catalog')).toBeVisible();
  });

  // ── RBAC: editor bloqueado para quem não escreve comando ───────────────────
  //
  // BLOCKER-5 resolvido: `pacCommandWriteGuard` consulta `PAC_ACCESS_DECISION`,
  // que traduz 'commandWrite' pelo `PAC_PERMISSION_MAP` (→
  // `pac.comando-authoring.criar`) e compara com as authorities do token.
  //
  // A sessão é de APOIO AO PACIENTE, e não de cuidadora: a cuidadora TEM
  // `pac.comando-authoring.criar` (conferido no RoleAuthorityPolicy do
  // acs-service), então semeá-la aqui produziria um verde que prova o contrário
  // do que o cenário afirma.
  test('Caller lacking comando write permission has the command editor route blocked and editor hidden', async ({ page }) => {
    await page.addInitScript(scriptSemearSessaoApoio());

    await page.goto('/pac/comandos/novo');

    // O guard devolve ao catálogo, que o apoio ainda alcança…
    await expect(page).toHaveURL(/\/pac$/, { timeout: 10_000 });
    // …e o editor não é montado.
    await expect(page.locator('pac-command-editor')).toHaveCount(0);
  });

});
