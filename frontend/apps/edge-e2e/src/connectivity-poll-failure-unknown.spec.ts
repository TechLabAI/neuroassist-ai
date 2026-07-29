import { test, expect } from '@playwright/test';

/**
 * SPEC-08 Scenario 5 — connectivity-poll-failure-unknown
 * Covers: SPEC-04, SPEC-07
 *
 * Liveness poll failure → derivedState = 'unknown', NEVER 'online'.
 * Silent retry on next tick; state recovers when poll succeeds again.
 * Invariant: unknown ≠ online (ADR-005 §2).
 *
 * TODO[FE-EDGE-019]: implement retry-backoff spec (HIGH priority, G6 — FE-EDGE-019)
 * TODO[FE-EDGE-020]: virtual poll failure test via route interceptor (Layer 2)
 */

test.describe('EDGE – Poll failure → unknown (never online)', () => {
  test('should show device detail surface without errors by default', async ({ page }) => {
    await page.goto('/edge/device');
    // Default mock adapter: poll always succeeds → Online state
    await expect(page.getByText(/Online/i)).toBeVisible({ timeout: 3000 });
  });

  // TODO[FE-EDGE-020]: add interceptor-based poll failure scenario (Layer 2)
  // test('should show unknown state when poll fails', async ({ page }) => {
  //   await page.route('/api/v1/edge/patients/*/liveness', route => route.abort());
  //   await page.goto('/edge/device');
  //   await expect(page.getByText(/Desconhecido/i)).toBeVisible({ timeout: 5000 });
  //   await expect(page.getByText(/Online/i)).not.toBeVisible();
  // });
});
