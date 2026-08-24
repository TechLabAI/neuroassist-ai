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
import { Page } from '@playwright/test';
import { scriptSemearSessao } from '@neuroassist/shared/auth-login/testing';

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
  await page.goto('/gest');
  await page.waitForSelector('gest-gesture-live-test', { timeout: 10_000 });
}
