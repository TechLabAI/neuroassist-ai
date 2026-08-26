/**
 * Shared E2E helpers for GEST Playwright specs (gest-e2e).
 *
 * These utilities handle common concerns:
 *  - Mocking navigator.mediaDevices.getUserMedia (no real camera hardware needed)
 *  - Navigating to the GEST dev host at /gest
 *
 * All addInitScript calls MUST be made before page.goto() so the overrides are
 * in place when Angular bootstraps and the facade calls getUserMedia.
 */
import { Page, test } from '@playwright/test';
import { scriptSemearSessao } from '@neuroassist/shared/auth-login/testing';
import { scriptSemAlarmeDeEmergencia } from '@neuroassist/evnt/testing';

// ---------------------------------------------------------------------------
// Camera mocks
// ---------------------------------------------------------------------------

/**
 * Mock getUserMedia to return a silent, empty MediaStream.
 * The facade's acquireCamera() succeeds; no real webcam needed.
 * Call BEFORE page.goto().
 */
export async function mockCameraSuccess(page: Page): Promise<void> {
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      get: () => ({
        getUserMedia: () => Promise.resolve(new MediaStream()),
        enumerateDevices: () => Promise.resolve([]),
      }),
    });
  });
}

/**
 * Mock getUserMedia to throw NotAllowedError (permission denied).
 * The facade's acquireCamera() fails → state: camera-error.
 * Call BEFORE page.goto().
 */
export async function mockCameraError(page: Page): Promise<void> {
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      get: () => ({
        getUserMedia: () =>
          Promise.reject(new DOMException('Permission denied', 'NotAllowedError')),
        enumerateDevices: () => Promise.resolve([]),
      }),
    });
  });
}

// ---------------------------------------------------------------------------
// Motor de detecção
// ---------------------------------------------------------------------------

/**
 * Força o motor de detecção SIMULADO (EdgeDetectionMockAdapter).
 *
 * Desde a ADR-007 a app roda o motor real (MediaPipe no browser). Aqui a webcam
 * é um MediaStream vazio — o motor real nunca emite gesto nenhum e a UI fica
 * presa em 'testing' indefinidamente. Todo cenário que exercita a máquina de
 * estados do facade (reconhecido, composto, timeout, erro de stream) precisa de
 * um motor determinístico e tem de chamar isto ANTES do goto.
 *
 * Cenários que só olham câmera/rota/ciclo de vida NÃO devem chamar: eles valem
 * mais rodando contra o motor de verdade.
 */
/**
 * Páginas que pediram o motor simulado.
 *
 * <p>Lembrado porque o pedido acontece ANTES do `goto` (o `provideGest` lê a
 * global uma vez, no bootstrap) e a verificação só é possível DEPOIS, quando há
 * página carregada. `WeakSet` para não segurar a página viva.
 */
const pediramMotorSimulado = new WeakSet<Page>();

export async function usarMotorSimulado(page: Page): Promise<void> {
  pediramMotorSimulado.add(page);
  await page.addInitScript(() => {
    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    (window as any).__GEST_E2E_DETECTION = 'mock';
  });
}

// ---------------------------------------------------------------------------
// Navigation
// ---------------------------------------------------------------------------

/**
 * Navigate to /gest (GestDevHostComponent) and wait for the live-test
 * component to mount. Uses a 10s timeout to account for lazy loading.
 */
export async function gotoGest(page: Page): Promise<void> {
  // /gest fica dentro do shell, que passou a exigir sessão. Semear aqui, e não em
  // cada spec, porque este é o único ponto por onde a suíte entra na página — e
  // tem de ser antes do goto: o AuthSessionStore lê o sessionStorage no construtor.
  await page.addInitScript(scriptSemearSessao());
  // O alarme de emergência da semente do EVNT preempta /gest também — este arquivo
  // já registrava isso num comentário do composition-boundary.spec e contornava a mão.
  await page.addInitScript(scriptSemAlarmeDeEmergencia());
  await page.goto('/gest');

  // Quem pediu o motor simulado precisa de um build de DESENVOLVIMENTO: o gancho
  // que troca o motor é fechado por `isDevMode()`, porque uma global capaz de
  // desligar a detecção de gesto não pode existir onde há paciente. Contra o
  // container (build de produção) estes cenários não têm como passar — e reprovar
  // ali daria a impressão de defeito onde há uma decisão deliberada.
  //
  // Pular DIZENDO o motivo é honesto; reprovar em silêncio, não; e um `skip`
  // incondicional esconderia a cobertura que existe em desenvolvimento.
  if (pediramMotorSimulado.has(page)) {
    const ehDesenvolvimento = await page.evaluate(() =>
      Boolean((globalThis as Record<string, unknown>)['__NEUROASSIST_DEV__']),
    );
    test.skip(
      !ehDesenvolvimento,
      'Requer build de desenvolvimento: o motor de detecção simulado é inerte em produção, por segurança.',
    );
  }
  await page.waitForSelector('gest-gesture-live-test', { timeout: 10_000 });
}

/**
 * Recusa seguir quando o gancho do motor simulado não pode funcionar.
 *
 * <p>O `__GEST_E2E_DETECTION` é honrado apenas em build de desenvolvimento
 * (`isDevMode()`), porque uma global capaz de desligar a detecção de gesto não
 * pode existir onde há paciente. Rodando contra o container (build de produção),
 * estes cenários não têm como passar — e reprovar ali dava a impressão de defeito
 * onde há uma decisão de segurança.
 *
 * <p>Pular com o motivo dito é honesto; reprovar em silêncio, não.
 */
export async function exigeBuildDeDesenvolvimento(page: Page): Promise<boolean> {
  return page.evaluate(() => Boolean((globalThis as Record<string, unknown>)['__NEUROASSIST_DEV__']));
}
