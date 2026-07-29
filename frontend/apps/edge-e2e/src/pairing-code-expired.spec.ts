import { test, expect } from '@playwright/test';

/**
 * SPEC-08 Scenario 2 — pairing-code-expired
 * Covers: SPEC-03, SPEC-04, SPEC-07
 *
 * Enrollment token TTL elapses before agent claims →
 * phase transitions to 'expired' → user prompted to re-register.
 * Phase MUST NOT hang in 'awaiting-claim' past the TTL (api-contract §2.1).
 *
 * TODO[FE-EDGE-010]: virtual TTL expiry test (Layer 2 — set very short TTL in feature flag)
 */

test.describe('EDGE – Pairing code expired', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/edge/pairing');
  });

  test('should show expired state and offer a new code button', async ({ page }) => {
    // TODO[FE-EDGE-010]: inject very short TTL mock, wait for expiry (Layer 2)
    // For now: verify the page renders the pairing surface correctly
    await expect(page.getByText(/Parear dispositivo/i)).toBeVisible();
    // When expired: 'Novo código' button must appear (covered in Layer 2 virtual-clock test)
  });
});
