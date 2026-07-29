/**
 * E2E spec: Onboarding — register paciente (step 2)
 * Feature: frontend/e2e/patient-register.feature
 * 8 scenarios (BDD -> Playwright stubs)
 * Gate: A5 e2e-validate (structural). Prototype-first (D-484), registered by
 * fe-nx-register-module.py (D-487).
 * TODO[FE-PAC-P9]: implement with real selectors after fe-implement.
 */
import { test, expect } from '@playwright/test';

test.describe('Onboarding — register paciente (step 2)', () => {
  // TODO[FE-PAC-P9]: implement assertions for this scenario
  test.skip('Register a paciente on the active empresa unlocks the command catalog step', async ({ page }) => {
    await page.goto('/pac');
    await expect(page).toHaveURL(/.*/);
  });

  // TODO[FE-PAC-P9]: implement assertions for this scenario
  test.skip('Caller lacking paciente write permission has the onboarding paciente save action disabled', async ({ page }) => {
    await page.goto('/pac');
    await expect(page).toHaveURL(/.*/);
  });

  // TODO[FE-PAC-P9]: implement assertions for this scenario
  test.skip('Blank nome keeps the onboarding paciente save action disabled', async ({ page }) => {
    await page.goto('/pac');
    await expect(page).toHaveURL(/.*/);
  });

  // TODO[FE-PAC-P9]: implement assertions for this scenario
  test.skip('Unknown dispositivo reference rejects the paciente with an inline validation error', async ({ page }) => {
    await page.goto('/pac');
    await expect(page).toHaveURL(/.*/);
  });

  // TODO[FE-PAC-P9]: implement assertions for this scenario
  test.skip('Unknown cuidador reference rejects the paciente with an inline validation error', async ({ page }) => {
    await page.goto('/pac');
    await expect(page).toHaveURL(/.*/);
  });

  // TODO[FE-PAC-P9]: implement assertions for this scenario
  test.skip('ACS unreachable during the paciente write makes the caregiver retry', async ({ page }) => {
    await page.goto('/pac');
    await expect(page).toHaveURL(/.*/);
  });

  // TODO[FE-PAC-P9]: implement assertions for this scenario
  test.skip('Missing-tenant on the paciente write shows a session banner prompting re-authentication', async ({ page }) => {
    await page.goto('/pac');
    await expect(page).toHaveURL(/.*/);
  });

  // TODO[FE-PAC-P9]: implement assertions for this scenario
  test.skip('Patient register feature composed inside the onboarding wizard contributes no PAC breadcrumb', async ({ page }) => {
    await page.goto('/pac');
    await expect(page).toHaveURL(/.*/);
  });

});
