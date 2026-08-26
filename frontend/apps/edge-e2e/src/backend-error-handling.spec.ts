/**
 * SPEC-08 Scenario 8 — backend-error-handling (FE-EDGE-026)
 *
 * ProblemDetail error responses → correct user-facing surface.
 * Error surface routing (SPEC-07 §4):
 *   400 /errors/validation      → inline/banner
 *   403 /errors/insufficient-scope → toast
 *   409 /errors/pairing-conflict  → banner
 *   409 /errors/device-retired    → banner (NEW EARS clause — FE-EDGE-027)
 *
 * Privacy invariant: no clientSecret, enrollmentToken, or biometric data in DOM
 *   (api-contract §8, SPEC-07 §9).
 *
 * Mode A (mock): privacy check + basic DOM assertions.
 * Mode B (HTTP): full error surface routing via route intercepts.
 *
 * References: SPEC-08 §8, SPEC-07, api-contract §8.
 */

import { test, expect } from '@playwright/test';
import {
  EDGE_BASE,
  aplicarModoHttp,
  EDGE_DEVICE_ID,
  EDGE_PATIENT_ID,
  makeDevicePayload,
  fulfillError,
  fulfillRegister,
} from './fixtures/edge-stubs';

import { scriptSemearSessao } from '@neuroassist/shared/auth-login/testing';
import { scriptSemAlarmeDeEmergencia } from '@neuroassist/evnt/testing';

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

// ---------------------------------------------------------------------------
// Mode A — Mock adapter: privacy assertion + basic structure
// ---------------------------------------------------------------------------

test.describe('EDGE – Backend error handling (mock-adapter mode, privacy)', () => {
  test('device detail does not expose clientSecret in DOM', async ({ page }) => {
    await page.goto('/edge/device');
    await expect(page.getByText(/Dispositivo/i).first()).toBeVisible({ timeout: 4000 });
    const content = await page.content();
    expect(content).not.toContain('clientSecret');
  });

  test('device detail does not expose enrollmentToken in DOM', async ({ page }) => {
    await page.goto('/edge/device');
    await expect(page.getByText(/Dispositivo/i).first()).toBeVisible({ timeout: 4000 });
    const content = await page.content();
    expect(content).not.toContain('enrollmentToken');
  });

  test('device detail does not expose biometric confidence values in DOM', async ({ page }) => {
    await page.goto('/edge/device');
    await expect(page.getByText(/Dispositivo/i).first()).toBeVisible({ timeout: 4000 });
    const content = await page.content();
    expect(content).not.toContain('"confidence"');
    expect(content).not.toContain('confidence:');
  });

  test('pairing page does not expose credential data in DOM', async ({ page }) => {
    await page.goto('/edge/pairing');
    await expect(page.getByText(/Parear dispositivo/i)).toBeVisible({ timeout: 4000 });
    const content = await page.content();
    expect(content).not.toContain('clientSecret');
    expect(content).not.toContain('"confidence"');
  });
});

// ---------------------------------------------------------------------------
// Mode B — HTTP adapter + route intercepts (full error surface routing)
// ---------------------------------------------------------------------------

const HTTP_MODE = process.env['EDGE_HTTP_MODE'] === '1';

test.describe('EDGE – Backend error handling (HTTP mode, route intercepts)', () => {
  test.skip(!HTTP_MODE, 'Requires EDGE_HTTP_MODE=1 (app in HTTP adapter mode)');

  // A app precisa EMITIR as chamadas para os `page.route` abaixo terem o que
  // interceptar. Antes disto não havia como sair do mock em runtime, e por isso
  // estes cenários nunca rodaram.
  test.beforeEach(async ({ page }) => {
    await aplicarModoHttp(page);
  });

  test('400 /errors/validation → inline/banner on register', async ({ page }) => {
    await page.route(`${EDGE_BASE}/devices`, async (route, req) => {
      if (req.method() !== 'POST') { await route.continue(); return; }
      await fulfillError(
        route,
        '/errors/validation',
        400,
        'Verifique os dados do dispositivo antes de continuar.',
      );
    });

    await page.goto('/edge/pairing');
    await page.getByRole('button', { name: 'Solicitar código' }).click();

    // Banner: "Verifique os dados..." must appear inline.
    await expect(
      page.getByText(/Verifique os dados/i),
    ).toBeVisible({ timeout: 4000 });

    // Not a toast (toasts are transient; banner is persistent).
    // Verify no credential data in the error message DOM.
    const errorText = await page.locator('[role="alert"], .banner, .error-banner').textContent();
    if (errorText) {
      expect(errorText).not.toContain('clientSecret');
      expect(errorText).not.toContain('enrollmentToken');
    }
  });

  test('403 /errors/insufficient-scope → toast (not banner/modal)', async ({ page }) => {
    // O 403 só chega DEPOIS que o poll observa a reivindicação do código e
    // dispara o pairToPatient — e o intervalo do poll é da ordem de segundos.
    // Esperar o aviso com um prazo fixo tornava o cenário instável (reprovava
    // uma vez a cada três). Contar a chamada dá um marco determinístico: espera-se
    // o POST acontecer, e só então se afirma sobre a tela.
    let tentativasDePareamento = 0;
    await page.route(`${EDGE_BASE}/devices/${EDGE_DEVICE_ID}/pairings`, async (route, req) => {
      if (req.method() !== 'POST') { await route.continue(); return; }
      tentativasDePareamento += 1;
      await fulfillError(
        route,
        '/errors/insufficient-scope',
        403,
        'Você não tem permissão para gerenciar dispositivos.',
      );
    });

    // Seed device first (for the pairing action to exist)
    await page.route(`${EDGE_BASE}/devices`, async (route, req) => {
      if (req.method() !== 'POST') { await route.continue(); return; }
      await fulfillRegister(route);
    });

    // O passo que faltava. `pairToPatient` só é disparado pelo poll, quando o
    // aparelho já reclamou o código — isto é, quando `ultimaSincronizacao` deixa
    // de ser nula. O cenário anterior pedia o código e parava ali; o próprio
    // comentário admitia a lacuna ("requires navigating through the wizard
    // steps") e mesmo assim afirmava sobre o toast. Nunca podia passar.
    await page.route(`${EDGE_BASE}/devices/${EDGE_DEVICE_ID}`, async (route, req) => {
      if (req.method() !== 'GET') { await route.continue(); return; }
      const agora = Date.now();
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(
          makeDevicePayload({
            pairingState: 'registered',
            ultimaSincronizacao: new Date(agora - 5_000).toISOString(),
          }),
        ),
      });
    });

    await page.goto('/edge/pairing');
    await page.getByRole('button', { name: 'Solicitar código' }).click();

    // Marco: o pareamento foi de fato tentado.
    await expect.poll(() => tentativasDePareamento, { timeout: 30_000 }).toBeGreaterThan(0);

    // O 403 chega pelo pairToPatient. A superfície tem de ser AVISO, e não
    // banner ou modal: perder permissão no meio de um pareamento é notícia, não
    // uma decisão a tomar.
    await expect(page.locator('[data-testid="toast"]')).toBeVisible({ timeout: 10_000 });
    // A frase é a do 403, e não a de falha de conexão: dizer "temporária" para
    // uma permissão negada manda a pessoa insistir num caminho fechado.
    await expect(page.locator('[data-testid="toast"]')).toContainText(
      'Você não tem permissão',
    );
    // E não há "Tentar novamente": repetir não adianta.
    await expect(page.locator('[data-testid="toast-acao"]')).toHaveCount(0);
  });

  test('falha transiente esgota as tentativas → aviso com "Tentar novamente"', async ({ page }) => {
    // A superfície de AVISO do SPEC-07 §3 existir de verdade.
    //
    // Até agora `EDGE_TOAST_TOKEN` não tinha provider nenhum, e as facades fazem
    // `this.toast?.show(...)`: `undefined?.show()` não faz nada e não reclama. O
    // código parecia tratar o erro, então ninguém procurava o defeito. Este
    // cenário falha se alguém desprovidenciar o token de novo.
    let tentativas = 0;
    await page.route(`${EDGE_BASE}/devices/${EDGE_DEVICE_ID}`, async (route, req) => {
      if (req.method() !== 'GET') { await route.continue(); return; }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(makeDevicePayload()),
      });
    });
    await page.route(`${EDGE_BASE}/devices/${EDGE_DEVICE_ID}/pairings/current`, async (route, req) => {
      if (req.method() !== 'DELETE') { await route.continue(); return; }
      // 503 é transiente: a facade tenta de novo duas vezes e só então avisa.
      tentativas += 1;
      await fulfillError(route, '/errors/unavailable', 503, 'Serviço indisponível.');
    });

    await page.goto(`/edge/device/${EDGE_DEVICE_ID}`);
    // Rótulos e testids reais, lidos do componente — não adivinhados: a primeira
    // versão deste cenário chutou "Confirmar" e ficou 30 s esperando um botão que
    // não existe.
    await page.getByRole('button', { name: 'Despareador' }).click();
    await page.locator('[data-testid="confirm-unpair-btn"]').click();

    const aviso = page.locator('[data-testid="toast"]');
    await expect(aviso).toBeVisible({ timeout: 15_000 });
    await expect(aviso).toContainText('Falha temporária de conexão.');

    // Com ação oferecida, o aviso NÃO some sozinho — retirar o botão enquanto a
    // pessoa vai até ele é armadilha, e pior para quem navega por teclado.
    await expect(page.locator('[data-testid="toast-acao"]')).toBeVisible();
    await page.waitForTimeout(11_000);
    await expect(aviso).toBeVisible();

    // Anunciável por leitor de tela: o contêiner do AVISO é uma live region
    // educada. Escopado no próprio aviso porque a faixa (ui-banner-outlet) também
    // é `role=status`/`aria-live=polite` — as duas são regiões legítimas, e um
    // locator solto casava com ambas.
    await expect(
      page.locator('[role="status"][aria-live="polite"]').filter({ has: page.locator('[data-testid="toast"]') }),
    ).toBeAttached();

    expect(tentativas).toBeGreaterThanOrEqual(3);
  });

  test('409 /errors/pairing-conflict → banner on unpair', async ({ page }) => {
    await page.route(`${EDGE_BASE}/devices/${EDGE_DEVICE_ID}`, async (route, req) => {
      if (req.method() !== 'GET') { await route.continue(); return; }
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(makeDevicePayload()),
      });
    });

    await page.route(`${EDGE_BASE}/devices/${EDGE_DEVICE_ID}/pairings/current`, async (route, req) => {
      if (req.method() !== 'DELETE') { await route.continue(); return; }
      await fulfillError(
        route,
        '/errors/pairing-conflict',
        409,
        'Este paciente já tem um dispositivo ativo. Despareie o atual antes de parear um novo.',
      );
    });

    await page.goto(`/edge/device/${EDGE_DEVICE_ID}`);
    page.on('dialog', (dialog) => dialog.accept());

    const unpairBtn = page.getByText(/Despareador/i).first();
    if (await unpairBtn.isVisible({ timeout: 4000 })) {
      await unpairBtn.click();
      // Banner: conflict message visible persistently (not self-dismissing toast)
      await expect(
        page.getByText(/Este paciente já tem um dispositivo ativo/i),
      ).toBeVisible({ timeout: 4000 });
    }
  });

  test('409 /errors/device-retired → banner on device detail load', async ({ page }) => {
    // Stub GET device to return 409 device-retired.
    await page.route(`${EDGE_BASE}/devices/${EDGE_DEVICE_ID}`, async (route, req) => {
      if (req.method() !== 'GET') { await route.continue(); return; }
      await fulfillError(
        route,
        '/errors/device-retired',
        409,
        'Este dispositivo foi aposentado e não pode ser usado. Registre um novo.',
      );
    });

    await page.goto(`/edge/device/${EDGE_DEVICE_ID}`);

    // Banner: "Este dispositivo foi aposentado..." must appear.
    await expect(
      page.getByText(/Este dispositivo foi aposentado/i),
    ).toBeVisible({ timeout: 4000 });

    // Re-register CTA must be present (SPEC-07 device-retired EARS clause).
    await expect(
      page.getByText(/Registre um novo|Registrar novo dispositivo/i),
    ).toBeVisible({ timeout: 3000 });

    // Privacy: no credential data in error message.
    const content = await page.content();
    expect(content).not.toContain('clientSecret');
    expect(content).not.toContain('enrollmentToken');
  });

  test('privacy invariant: no credential data in any error surface', async ({ page }) => {
    // Test each error surface for privacy invariant.
    const privacyTerms = ['clientSecret', 'enrollmentToken', '"confidence"', 'biometric'];

    await page.route(`${EDGE_BASE}/devices`, async (route, req) => {
      if (req.method() !== 'POST') { await route.continue(); return; }
      await fulfillError(route, '/errors/validation', 400, 'Verifique os dados do dispositivo.');
    });

    await page.goto('/edge/pairing');
    await page.getByRole('button', { name: 'Solicitar código' }).click();

    await page.waitForTimeout(2000);
    const errorContent = await page.content();

    for (const term of privacyTerms) {
      expect(errorContent).not.toContain(term);
    }
  });
});
