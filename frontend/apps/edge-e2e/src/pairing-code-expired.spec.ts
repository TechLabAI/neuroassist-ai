/**
 * SPEC-08 Scenario 2 — pairing-code-expired (FE-EDGE-022)
 *
 * Enrollment token TTL elapses before agent claims →
 * phase transitions to 'expired' → user prompted to re-register.
 * Phase MUST NOT hang in 'awaiting-claim' past the TTL (api-contract §2.1, SPEC-03 §7).
 *
 * Two modes:
 *   A. MOCK adapter: pairing code card renders; use Playwright clock.setFixedTime() to
 *      advance past a very short enrollmentExpiresAt.
 *   B. HTTP adapter + route intercepts: stub register with enrollmentExpiresAt = now+1s,
 *      advance clock by 2s, assert expired state.
 *
 * Safety-critical: a stale token must never silently hang or auto-advance to paired
 * without a real agent claim (SPEC-03 §7 code-TTL invariant).
 *
 * References: SPEC-08 §2, SPEC-03 §7, api-contract §2.1.
 */

import { test, expect } from '@playwright/test';
import { EDGE_BASE, fulfillRegister, EDGE_DEVICE_ID, makeDevicePayload, aplicarModoHttp } from './fixtures/edge-stubs';

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
// Mode A — Mock adapter: uses Playwright virtual clock to expire the token
// The mock adapter returns `enrollmentExpiresAt = now + 5min`, so we install
// a fake clock, advance past that boundary, and assert the expired UI.
// ---------------------------------------------------------------------------


// A tela ociosa traz o rótulo "Solicitar código" DUAS vezes: no botão e na frase
// de instrução `Clique em "Solicitar código" para iniciar o pareamento`. Não é
// defeito — a instrução cita o botão de propósito —, mas obriga o teste a dizer
// qual dos dois quer. `getByRole('button')` com nome exato é o locator honesto.
const botaoSolicitarCodigo = (page: import('@playwright/test').Page) =>
  page.getByRole('button', { name: 'Solicitar código' });

// O rótulo da fase corrente é ecoado na linha "Estado: …" no rodapé da página,
// com o MESMO texto do cartão de desfecho ("Dispositivo pareado!", "Código
// expirado"). Quem representa o desfecho é o cartão — escopamos nele.
const cartaoResultado = (page: import('@playwright/test').Page) =>
  page.locator('edge-pairing-result');

test.describe('EDGE – Pairing code expired (mock-adapter mode)', () => {
  test('pairing page renders the pairing surface correctly', async ({ page }) => {
    await page.goto('/edge/pairing');
    await expect(page.getByText(/Parear dispositivo/i)).toBeVisible({ timeout: 4000 });
  });

  test('pairing page shows re-register CTA when token expires (virtual clock)', async ({ page }) => {
    // Install virtual clock BEFORE page loads so Angular uses the frozen time.
    await page.clock.install({ time: new Date('2026-08-19T10:00:00.000Z') });

    await page.goto('/edge/pairing');
    await botaoSolicitarCodigo(page).click();

    // Wait for the token to appear (mock: 400ms delay)
    await expect(page.getByText(/MOCK-TOKEN/i)).toBeVisible({ timeout: 2000 });

    // Advance clock past the mock token TTL (mock returns 5 min = 300s).
    // Advance by 310 seconds to be safely past the expiry.
    // Em milissegundos, e não na forma '5:30': o relógio do Playwright aceita
    // 'mm:ss' apenas com dois dígitos no primeiro campo, e '5:30' era rejeitado
    // em tempo de execução ("Clock only understands numbers, 'mm:ss' and
    // 'hh:mm:ss'"). O erro estava aqui desde sempre; só não aparecia porque o
    // teste morria antes, no clique ambíguo em "Solicitar código".
    await page.clock.fastForward(330_000); // 5m30s — bem além do TTL de 5 min do mock

    // After TTL expiry the PairingCodeCardComponent should show "Código expirado"
    // and the re-register CTA must be present (SPEC-03 §7).
    // The mock adapter timer fires a TTL check via startTtlWatch().
    // In mock mode the token expires at Date.now() + 5min — if clock is advanced
    // the expired state should trigger (phase = 'expired').
    // Duas asserções específicas no lugar da alternância solta: o cartão de
    // desfecho declara a expiração E oferece o CTA de novo código. O regex
    // original casava também com a linha "Estado: Código expirado" do rodapé —
    // três nós para um locator estrito. Separar aperta a asserção em vez de
    // afrouxá-la: agora exige as duas coisas, não uma qualquer das três.
    await expect(
      cartaoResultado(page).getByText('Código expirado'),
    ).toBeVisible({ timeout: 5000 });
    await expect(
      cartaoResultado(page).getByRole('button', { name: 'Novo código' }),
    ).toBeVisible();
  });
});

// ---------------------------------------------------------------------------
// Mode B — HTTP adapter + route intercepts
// ---------------------------------------------------------------------------

const HTTP_MODE = process.env['EDGE_HTTP_MODE'] === '1';

test.describe('EDGE – Pairing code expired (HTTP mode, route intercepts)', () => {
  test.skip(!HTTP_MODE, 'Requires EDGE_HTTP_MODE=1 (app in HTTP adapter mode)');

  // A app precisa EMITIR as chamadas para os `page.route` abaixo terem o que
  // interceptar. Antes disto não havia como sair do mock em runtime, e por isso
  // estes cenários nunca rodaram.
  test.beforeEach(async ({ page }) => {
    await aplicarModoHttp(page);
  });

  test('shows expired state after short-TTL token elapses', async ({ page }) => {
    // Install virtual clock so we can fast-forward time without real waits.
    //
    // O prazo do token TEM de ser calculado a partir DESTA base, não de
    // `Date.now()`: o handler da rota roda no Node (hora real), a página roda no
    // relógio virtual, e as duas estavam a dias de distância. O resultado era um
    // "TTL de 1 segundo" que a página lia como "expira em 468556s" — o token
    // nunca expirava e o cenário media o oposto do que pretendia.
    const BASE_EPOCH = new Date('2026-08-19T10:00:00.000Z').getTime();
    await page.clock.install({ time: BASE_EPOCH });

    // Stub register: token expires in 1 second (very short TTL).
    await page.route(`${EDGE_BASE}/devices`, async (route, req) => {
      if (req.method() !== 'POST') { await route.continue(); return; }
      await fulfillRegister(route, {
        enrollmentToken: 'SHORT-TTL-TOKEN',
        // TTL de 30s, não de 1s: `clock.install` deixa o tempo correr junto com o
        // relógio real, então um TTL de 1 segundo expirava DURANTE o ida-e-volta
        // do registro — o cartão do código nunca chegava a ser visto e o cenário
        // morria antes de exercitar a expiração. 30s dá folga para o token
        // aparecer, e o `fastForward` abaixo é quem faz o prazo vencer.
        enrollmentExpiresAt: new Date(BASE_EPOCH + 30_000).toISOString(),
      });
    });

    // Stub device GET (claim poll returns registered, unclaimed)
    await page.route(`${EDGE_BASE}/devices/${EDGE_DEVICE_ID}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(makeDevicePayload({
          pairingState: 'registered',
          ultimaSincronizacao: null,
          status: 'n/a',
        })),
      });
    });

    await page.goto('/edge/pairing');
    await page.getByRole('button', { name: 'Solicitar código' }).click();

    // Token appears
    await expect(page.getByText(/SHORT-TTL-TOKEN/i)).toBeVisible({ timeout: 3000 });

    // Advance clock past the 30s TTL
    await page.clock.fastForward(31_000);

    // Assertions (SPEC-08 §2):
    // 1. "Código expirado" visible
    //
    // Escopado no cartão de desfecho, como no bloco de mock: a alternância solta
    // casava com três nós (o título do cartão, a frase explicativa e a linha
    // "Estado: Código expirado" do rodapé). Exigir os dois nós certos do cartão
    // aperta a asserção — antes bastava um qualquer dos três.
    await expect(
      cartaoResultado(page).getByText('Código expirado'),
    ).toBeVisible({ timeout: 3000 });
    // 2. Copy button gone (no point copying an expired token)
    await expect(page.getByRole('button', { name: /Copiar código/i })).not.toBeVisible();
    // 3. Re-register CTA present
    await expect(
      cartaoResultado(page).getByRole('button', { name: 'Novo código' }),
    ).toBeVisible({ timeout: 2000 });

    // 4. Click re-register → new POST /devices called
    //
    // `page.on` é síncrono (não devolve Promise) — o `await` original não
    // esperava nada e só disfarçava isso.
    const registerCalls: string[] = [];
    page.on('request', (req) => {
      if (req.method() === 'POST' && req.url().includes('/devices')) {
        registerCalls.push(req.url());
      }
    });
    await page.getByRole('button', { name: 'Novo código' }).click();
    // register() must be called again for a fresh token
    await expect.poll(() => registerCalls.length, { timeout: 5000 }).toBeGreaterThan(0);
  });
});
