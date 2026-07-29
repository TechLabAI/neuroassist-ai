import { test, expect } from '@playwright/test';

/**
 * SPEC-08 Scenario 8 — backend-error-handling
 * Covers: SPEC-07
 *
 * ProblemDetail error responses → correct user-facing surface.
 * No credential or biometric data in error messages (privacy invariant).
 *
 * TODO[FE-EDGE-024..027]: implement error surface tests (Layer 2 — HTTP adapter live)
 */

test.describe('EDGE – Backend error handling', () => {
  test('device detail should not expose credentials in error messages', async ({ page }) => {
    await page.goto('/edge/device');
    // With mock adapter: no error state → just verify no credential text leaks
    // In Layer 2 this test will inject 4xx ProblemDetail responses
    const pageContent = await page.content();
    expect(pageContent).not.toContain('clientSecret');
    expect(pageContent).not.toContain('enrollmentToken'); // not in DOM after use
    expect(pageContent).not.toContain('confidence'); // internal field, never in UI
  });
});
