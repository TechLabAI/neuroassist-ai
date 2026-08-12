/**
 * E2E spec: Tenant onboarding — provision empresa and register primary caregiver
 * Feature: frontend/e2e/tenant-onboarding.feature
 * 8 scenarios (BDD -> Playwright stubs)
 * Gate: A5 e2e-validate (structural). Prototype-first (D-484), registered by
 * fe-nx-register-module.py (D-487).
 * TODO[FE-ACS-P9]: implement with real selectors after fe-implement.
 */
import { test, expect } from '@playwright/test';

test.describe('Tenant onboarding — provision empresa and register primary caregiver', () => {
  // TODO[FE-ACS-P9]: implement assertions for this scenario
  test.skip('Provisioning an empresa that returns active writes the active empresa context', async ({ page }) => {
    await page.goto('/acs');
    await expect(page).toHaveURL(/.*/);
  });

  // TODO[FE-ACS-P9]: implement assertions for this scenario
  test.skip('Provisioning that is not yet confirmed shows a provisioning empresa state', async ({ page }) => {
    await page.goto('/acs');
    await expect(page).toHaveURL(/.*/);
  });

  // TODO[FE-ACS-P9]: implement assertions for this scenario
  test.skip('A provisioning retry that succeeds writes the active empresa context', async ({ page }) => {
    await page.goto('/acs');
    await expect(page).toHaveURL(/.*/);
  });

  // TODO[FE-ACS-P9]: implement assertions for this scenario
  test.skip('A provisioning retry that is still not active shows a retryable empresa banner', async ({ page }) => {
    await page.goto('/acs');
    await expect(page).toHaveURL(/.*/);
  });

  // TODO[FE-ACS-P9]: implement assertions for this scenario
  test.skip('The criar empresa action is disabled without tenant write permission', async ({ page }) => {
    await page.goto('/acs');
    await expect(page).toHaveURL(/.*/);
  });

  // TODO[FE-ACS-P9]: implement assertions for this scenario
  test.skip('Registering the primary caregiver and linking it covers the paciente', async ({ page }) => {
    await page.goto('/acs');
    await expect(page).toHaveURL(/.*/);
  });

  // TODO[FE-ACS-P9]: implement assertions for this scenario
  test.skip('The composed tenant setup and caregiver link steps contribute no ACS breadcrumb', async ({ page }) => {
    await page.goto('/acs');
    await expect(page).toHaveURL(/.*/);
  });

  // TODO[FE-ACS-P9]: implement assertions for this scenario
  test.skip('The PAC and EDGE onboarding steps are composed only at the shell page level', async ({ page }) => {
    await page.goto('/acs');
    await expect(page).toHaveURL(/.*/);
  });

});
