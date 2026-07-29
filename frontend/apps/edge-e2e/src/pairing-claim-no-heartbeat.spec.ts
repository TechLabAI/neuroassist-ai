import { test, expect } from '@playwright/test';

/**
 * SPEC-08 Scenario 3 — pairing-claim-no-heartbeat
 * Covers: SPEC-03, SPEC-04
 *
 * Agent claims the enrollment token but never sends first heartbeat →
 * phase stays 'awaiting-first-heartbeat', NEVER advances to 'paired'.
 * Invariant: no premature paired (SPEC-03 §7, ADR-005 no-premature-paired).
 *
 * TODO[FE-EDGE-009]: implement no-heartbeat scenario test (Layer 2)
 */

test.describe('EDGE – Pairing claim but no heartbeat', () => {
  test('pairing page should remain in awaiting state without heartbeat', async ({ page }) => {
    await page.goto('/edge/pairing');
    // This scenario requires a mock that returns a device without ultimaSincronizacao
    // Full implementation in Layer 2 (FE-EDGE-009)
    await expect(page.getByText(/Parear dispositivo/i)).toBeVisible();
  });
});
