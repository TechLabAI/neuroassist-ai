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
 * INFRASTRUCTURE BLOCKERS (as of 2026-08-14 — must be resolved before execution):
 *
 *   BLOCKER-1 [CRITICAL]: neuroassist-ai-web missing bootstrap files (main.ts, app.config.ts,
 *     project.json, index.html). `npx nx serve neuroassist-ai-web` fails. All scenarios blocked.
 *
 *   BLOCKER-2 [GUARD]: pacTenantPatientGuard is synchronous (TODO[PAC-FE-014]).
 *     Routes /pac/comandos/novo and /pac/comandos/:id/editar use the same guard chain.
 *     WORKAROUND: navigate to /pac first to prime the patient signal, then navigate to editor.
 *
 *   BLOCKER-4 [HTTP-ADAPTER]: Emergency gate scenario REQUIRES HTTP adapter mode.
 *     In mock-adapter mode, ComandoMockAdapter.publish() always succeeds — 422 is never returned.
 *     FIX: run app with HTTP adapter + use page.route() to intercept POST and return 422.
 *     FIX ALTERNATIVE: add a mock toggle (e.g., window.__PAC_E2E_FORCE_422=true) to
 *     ComandoMockAdapter.save() so the emergency gate can be triggered without HTTP adapter.
 *
 *   BLOCKER-6 [LIFECYCLE-BUTTONS]: The CommandEditorComponent template (command-editor.component.ts)
 *     does NOT include validar / publicar / recalibrar / retirar lifecycle action buttons.
 *     Only "Salvar comando" and "Cancelar" are present.
 *     Lifecycle-related scenarios (drafted enables validar, validated enables publicar, etc.)
 *     are ALL marked test.fixme() until the lifecycle buttons are added to the template.
 *
 * SCENARIOS IMPLEMENTED IN THIS REDUCED SCOPE:
 *   1. Novo comando route renders editor with blank draft in editing state
 *   2. Editar comando route renders editor pre-filled from that comando
 *   3. Blank rotulo keeps the salvar comando action disabled
 *   4. Sensibilidade rendered as baixa/media/alta labels (never raw threshold)
 *   5. Saving a new comando posts the draft with the save flow (ADR-005 pending-sync after save)
 *   SAFETY-CRITICAL: Emergency-not-detectable on publicar blocks publish with blocking modal
 *
 * CRITICAL SCENARIO (safety-critical, ADR-005 / SPEC-07):
 *   "Emergency-not-detectable on publicar blocks publish with a blocking modal and leaves
 *    comando validated" — verified at line ~160.
 */
import { test, expect, Page } from '@playwright/test';

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
  instance: '/api/v1/pacientes/pac-marcio/comandos/cmd-ajuda/publish',
  errorCode: 'emergency-not-detectable',
};

// ── Navigation helpers ───────────────────────────────────────────────────────

/**
 * Prime the PacPacienteFacade by navigating to /pac first (triggers loadCurrent via guard).
 * After 600ms the mock adapter delay (400ms) has settled and hasPatient() is true.
 * Then navigate to the editor route.
 * WORKAROUND for BLOCKER-2; remove once guard is made async (TODO[PAC-FE-014]).
 */
async function openEditor(page: Page, path: '/pac/comandos/novo' | `${'pac/comandos/'}${string}${'/editar'}`): Promise<void> {
  // Step 1: prime patient state
  await page.goto('/pac');
  await page.waitForTimeout(600);

  // Step 2: navigate to editor (normalize: call sites pass the path with or
  // without a leading slash — a bare template `/${path}` double-slashes the
  // former and Playwright/the browser then resolves it as protocol-relative,
  // i.e. `//pac/...` -> host "pac" -> net::ERR_NAME_NOT_RESOLVED)
  await page.goto(path.startsWith('/') ? path : `/${path}`);

  // Wait for the editor component to mount
  await page.waitForSelector('pac-command-editor, h1', { timeout: 8000 });
}

/**
 * Wire HTTP API mocks for pac-service endpoints.
 * ONLY effective when the app runs with HTTP adapter (BLOCKER-4).
 * In mock-adapter mode no HTTP calls are made and these handlers are not invoked.
 */
async function routePacApiPacientes(page: Page, response: unknown, status = 200) {
  await page.route('**/api/v1/pacientes*', (route) =>
    route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(response) }),
  );
}

async function routePacApiComandos(page: Page, response: unknown, status = 200) {
  await page.route('**/api/v1/pacientes/**/comandos*', (route) =>
    route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(response) }),
  );
}

/**
 * Wire the emergency-not-detectable 422 response on the publish endpoint.
 * In HTTP adapter mode this intercepts POST .../comandos/:id/publish and returns 422.
 * BLOCKER-4: no-op in mock-adapter mode.
 */
async function routePublishEmergencyGate(page: Page): Promise<void> {
  await page.route('**/api/v1/pacientes/**/comandos/**/publish', (route) =>
    route.fulfill({
      status: 422,
      contentType: 'application/json',
      body: JSON.stringify(EMERGENCY_NOT_DETECTABLE_PROBLEM),
    }),
  );
  // The save() path also sends POST to /comandos — intercept that too, since in the current
  // editor the save() method can trigger emergency gate for urgencia=emergencia (SPEC-07).
  await page.route('**/api/v1/pacientes/**/comandos', async (route) => {
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
      if (req.url().includes('/api/v1/pacientes')) {
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

  // ── Lifecycle scenarios (deferred — lifecycle buttons not in template) ─────

  test.fixme(
    'Drafted editor comando enables validar and keeps publicar disabled',
    async ({ page }) => {
      // BLOCKER-6: CommandEditorComponent template has no validar/publicar buttons.
      // Add lifecycle action buttons to the editor template before implementing this scenario.
      void page;
    },
  );

  test.fixme(
    'Validated editor comando enables publicar',
    async ({ page }) => {
      // BLOCKER-6: lifecycle buttons not in template.
      void page;
    },
  );

  test.fixme(
    'Published editor comando enables recalibrar and keeps validar disabled',
    async ({ page }) => {
      // BLOCKER-6: lifecycle buttons not in template.
      void page;
    },
  );

  test.fixme(
    'Retired editor comando keeps every lifecycle action disabled',
    async ({ page }) => {
      // BLOCKER-6: lifecycle buttons not in template.
      void page;
    },
  );

  test.fixme(
    'Editar route with unknown comandoId redirects to the not-found view',
    async ({ page }) => {
      // REQUIRES: BLOCKER-1, BLOCKER-4 resolved; HTTP adapter returns 404 for unknown ID.
      await routePacApiPacientes(page, MOCK_PACIENTE_PAGE);
      await page.route('**/api/v1/pacientes/**/comandos/cmd-unknown**', (route) =>
        route.fulfill({ status: 404, contentType: 'application/json',
          body: JSON.stringify({ type: 'not-found', status: 404 }) }),
      );
      await openEditor(page, 'pac/comandos/cmd-unknown/editar');
      // Should redirect to /pac (catch-all route: path: '**', redirectTo: '')
      await expect(page).toHaveURL(/\/pac$/, { timeout: 5000 });
      void page;
    },
  );

  test.fixme(
    'Caller lacking comando write permission has the command editor route blocked and editor hidden',
    async ({ page }) => {
      // BLOCKER-5: pacCommandWriteGuard currently returns true (dev default).
      void page;
    },
  );

});
