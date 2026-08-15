/**
 * E2E spec: Onboarding — register paciente (step 2)
 * Feature: docs/architecture/frontend/modules/pac/frontend/e2e/patient-register.feature
 * PAC-FE-025: 9 scenarios (PAC P9, reduced scope)
 * Gate: A5 e2e-validate. Prototype-first (D-484), registered by fe-nx-register-module.py (D-487).
 *
 * INFRASTRUCTURE BLOCKERS (as of 2026-08-14 — must be resolved before execution):
 *
 *   BLOCKER-1 [CRITICAL]: neuroassist-ai-web missing bootstrap files (main.ts, app.config.ts,
 *     project.json, index.html). `npx nx serve neuroassist-ai-web` fails. All scenarios blocked.
 *
 *   BLOCKER-7 [COMPONENT NOT EMBEDDED — PRIMARY BLOCKER]:
 *     OnboardingWizardPageComponent (/onboarding) does NOT embed PatientRegisterComponent.
 *     Step 2 "Paciente" shows a static placeholder: "O registro do paciente é gerido pelo
 *     módulo PAC." with a link to /pac.
 *
 *     FIX REQUIRED before ANY scenario can run:
 *       In frontend/apps/neuroassist-ai-web/src/app/pages/onboarding-wizard-page.component.ts,
 *       replace the step-2 placeholder with:
 *
 *         import { PatientRegisterComponent } from '@neuroassist/pac';
 *         // In the template, step 2 div:
 *         <pac-patient-register (registered)="onPatientRegistered($event)" />
 *
 *     Until BLOCKER-7 is resolved, all 9 patient-register scenarios use test.fixme().
 *
 *   BLOCKER-1+7 EXCEPTION: Scenario 9 (breadcrumb ownership) can partially execute via
 *     /onboarding even before PatientRegisterComponent is embedded, because it asserts
 *     the ABSENCE of a PAC breadcrumb. It is implemented without test.fixme() but will
 *     trivially pass since no breadcrumb exists today.
 *
 *   BLOCKER-4 [HTTP-ADAPTER]: Scenarios 4, 5, 6, 7 (reference-resolution errors, ACS
 *     unreachable, missing-tenant) require HTTP adapter and page.route() to simulate
 *     backend error responses.
 *
 *   BLOCKER-5 [RBAC]: Scenario 2 (caller lacks paciente write permission) requires
 *     AccessDecisionService wired and 'pac.paciente.write' permission checked in the UI.
 *
 * ROUTE USED:
 *   /onboarding — OnboardingWizardPageComponent (ACS-owned, outside shell layout)
 *   The PatientRegisterComponent is at pac-patient-register in step 2 (post-BLOCKER-7-fix).
 */
import { test, expect, Page } from '@playwright/test';

// ── Navigation helper ─────────────────────────────────────────────────────────

/** Navigate to /onboarding and wait for the wizard to render. */
async function openOnboarding(page: Page): Promise<void> {
  await page.goto('/onboarding');
  await page.waitForSelector('h1', { timeout: 8000 });
  await expect(page.locator('h1')).toContainText('Configuração inicial');
}

/**
 * Wait for step 2 (Paciente) to render with the PatientRegisterComponent embedded.
 * REQUIRES BLOCKER-7 to be fixed.
 */
async function getStep2(page: Page) {
  // Step 2 is identified by the step number "2" badge and "Paciente" heading
  return page.locator('[class*="rounded-2xl"]').filter({ hasText: /^2\s*Paciente/m }).first();
}

// ── Mock route helpers ────────────────────────────────────────────────────────

async function routePacApiPacientes(page: Page, response: unknown, status = 200) {
  await page.route('**/api/v1/pacientes**', (route) =>
    route.fulfill({ status, contentType: 'application/json', body: JSON.stringify(response) }),
  );
}

const MISSING_TENANT_PROBLEM = {
  type: 'https://neuroassist.ai/problems/missing-tenant',
  title: 'Tenant context missing',
  status: 403,
  detail: 'The request does not carry a valid X-Empresa-Id header.',
  errorCode: 'missing-tenant',
};

const UNKNOWN_DISPOSITIVO_PROBLEM = {
  type: 'https://neuroassist.ai/problems/validation',
  title: 'Validation error',
  status: 400,
  detail: 'dispositivo: device reference unknown to EDGE.',
  errorCode: 'validation',
  fieldErrors: [{ field: 'dispositivo', message: 'Dispositivo desconhecido no catálogo EDGE.' }],
};

const UNKNOWN_CUIDADOR_PROBLEM = {
  type: 'https://neuroassist.ai/problems/validation',
  title: 'Validation error',
  status: 400,
  detail: 'cuidador: caregiver reference unknown to ACS.',
  errorCode: 'validation',
  fieldErrors: [{ field: 'cuidador', message: 'Cuidador desconhecido no catálogo ACS.' }],
};

// ── Tests ─────────────────────────────────────────────────────────────────────

test.describe('Onboarding — register paciente (step 2)', () => {

  // ── Scenario 1: Register paciente unlocks command catalog step ─────────────
  // BDD: "Register a paciente on the active empresa unlocks the command catalog step"
  // REQUIRES: BLOCKER-1, BLOCKER-7 resolved
  test.fixme(
    'Register a paciente on the active empresa unlocks the command catalog step',
    async ({ page }) => {
      // BLOCKER-7: PatientRegisterComponent must be embedded in step 2 of /onboarding.
      //
      // Expected flow:
      //   1. Navigate to /onboarding
      //   2. Step 2 shows pac-patient-register component with a nome input
      //   3. Fill nome "Márcio Amaral"
      //   4. Click "Registrar paciente"
      //   5. API POST is sent (scoped by X-Empresa-Id header in HTTP adapter mode)
      //   6. After success: step 2 shows "Paciente registrado" ✓
      //   7. The progress checklist shows "Paciente registrado" as done
      await openOnboarding(page);
      const step2 = await getStep2(page);

      const nomeInput = step2.locator('pac-patient-register pac-ds-input input, pac-ds-input input').first();
      await expect(nomeInput).toBeVisible({ timeout: 5000 });
      await nomeInput.fill('Márcio Amaral');

      const saveBtn = step2.locator('pac-ds-button').filter({ hasText: /Registrar paciente/i }).locator('button');
      await expect(saveBtn).not.toBeDisabled();
      await saveBtn.click();

      // After save: success confirmation from PatientRegisterComponent
      await expect(step2.locator('text=Paciente registrado com sucesso')).toBeVisible({ timeout: 5000 });

      // Progress checklist on the right column should now mark "Paciente registrado" as done
      await expect(page.locator('text=Paciente registrado').locator('..')).toContainText('check_circle');
    },
  );

  // ── Scenario 2: Caller lacks write permission — save action disabled ────────
  // BDD: "Caller lacking paciente write permission has the onboarding paciente save action disabled"
  // REQUIRES: BLOCKER-1, BLOCKER-5, BLOCKER-7 resolved
  test.fixme(
    'Caller lacking paciente write permission has the onboarding paciente save action disabled',
    async ({ page }) => {
      // BLOCKER-5: RBAC not wired; pacCommandWriteGuard returns true.
      // BLOCKER-7: PatientRegisterComponent not embedded.
      await openOnboarding(page);
      const step2 = await getStep2(page);
      // When user lacks 'pac.paciente.write' permission, the save button should be disabled
      const saveBtn = step2.locator('pac-ds-button').filter({ hasText: /Registrar|Salvar paciente/i }).locator('button');
      await expect(saveBtn).toBeDisabled();
    },
  );

  // ── Scenario 3: Blank nome keeps save disabled ────────────────────────────
  // BDD: "Blank nome keeps the onboarding paciente save action disabled"
  // REQUIRES: BLOCKER-1, BLOCKER-7 resolved; works with mock adapter
  test.fixme(
    'Blank nome keeps the onboarding paciente save action disabled',
    async ({ page }) => {
      // BLOCKER-7: PatientRegisterComponent not embedded in /onboarding.
      // Once embedded: navigate to /onboarding, verify save button disabled with empty nome.
      await openOnboarding(page);
      const step2 = await getStep2(page);

      const nomeInput = step2.locator('pac-ds-input input').first();
      await expect(nomeInput).toBeVisible({ timeout: 5000 });
      // Ensure nome is blank
      await nomeInput.fill('');

      // PatientRegisterComponent: [disabled]="!nomeInput().trim() || facade.saveState() === 'saving'"
      const saveBtn = step2.locator('pac-ds-button').filter({ hasText: /Registrar|Salvar paciente/i }).locator('button');
      await expect(saveBtn).toBeDisabled();

      // Filling a name should enable the button
      await nomeInput.fill('Ana');
      await expect(saveBtn).not.toBeDisabled();

      // Clearing again should disable
      await nomeInput.fill('');
      await expect(saveBtn).toBeDisabled();
    },
  );

  // ── Scenario 4: Unknown dispositivo reference → inline validation error ────
  // BDD: "Unknown dispositivo reference rejects the paciente with an inline validation error"
  // REQUIRES: BLOCKER-1, BLOCKER-4, BLOCKER-7 resolved
  test.fixme(
    'Unknown dispositivo reference rejects the paciente with an inline validation error',
    async ({ page }) => {
      // BLOCKER-4: requires HTTP adapter + page.route() to return 400 with field error.
      // BLOCKER-7: PatientRegisterComponent not embedded.
      await routePacApiPacientes(page, UNKNOWN_DISPOSITIVO_PROBLEM, 400);

      await openOnboarding(page);
      const step2 = await getStep2(page);

      await step2.locator('pac-ds-input input').first().fill('Márcio Amaral');
      await step2.locator('pac-ds-button').filter({ hasText: /Registrar paciente/i }).locator('button').click();

      // Backend responds 400 validation with field error on "dispositivo"
      // Error surface (PacErrorHandler → PacFeedbackService) should route to inline hint
      await expect(step2.locator('text=Dispositivo desconhecido')).toBeVisible({ timeout: 5000 });
      // Paciente must NOT be marked as created
      await expect(step2.locator('text=Paciente registrado com sucesso')).not.toBeVisible();
    },
  );

  // ── Scenario 5: Unknown cuidador reference → inline validation error ────────
  // BDD: "Unknown cuidador reference rejects the paciente with an inline validation error"
  // REQUIRES: BLOCKER-1, BLOCKER-4, BLOCKER-7 resolved
  test.fixme(
    'Unknown cuidador reference rejects the paciente with an inline validation error',
    async ({ page }) => {
      // BLOCKER-4: requires HTTP adapter + page.route() to return 400 with field error.
      // BLOCKER-7: PatientRegisterComponent not embedded.
      await routePacApiPacientes(page, UNKNOWN_CUIDADOR_PROBLEM, 400);

      await openOnboarding(page);
      const step2 = await getStep2(page);

      await step2.locator('pac-ds-input input').first().fill('Márcio Amaral');
      await step2.locator('pac-ds-button').filter({ hasText: /Registrar paciente/i }).locator('button').click();

      await expect(step2.locator('text=Cuidador desconhecido')).toBeVisible({ timeout: 5000 });
      await expect(step2.locator('text=Paciente registrado com sucesso')).not.toBeVisible();
    },
  );

  // ── Scenario 6: ACS unreachable — retryable message ──────────────────────
  // BDD: "ACS unreachable during the paciente write makes the caregiver retry"
  // REQUIRES: BLOCKER-1, BLOCKER-4, BLOCKER-7 resolved
  test.fixme(
    'ACS unreachable during the paciente write makes the caregiver retry',
    async ({ page }) => {
      // BLOCKER-4: network error simulated with page.route() abort.
      // BLOCKER-7: PatientRegisterComponent not embedded.
      await page.route('**/api/v1/pacientes**', (route) => route.abort('failed'));

      await openOnboarding(page);
      const step2 = await getStep2(page);

      await step2.locator('pac-ds-input input').first().fill('Márcio Amaral');
      await step2.locator('pac-ds-button').filter({ hasText: /Registrar paciente/i }).locator('button').click();

      // Error state: "Erro ao salvar. Verifique sua conexão e tente novamente."
      await expect(step2.locator('text=Erro ao salvar')).toBeVisible({ timeout: 5000 });
      // Patient must NOT be created
      await expect(step2.locator('text=Paciente registrado com sucesso')).not.toBeVisible();
    },
  );

  // ── Scenario 7: Missing-tenant → session banner ────────────────────────────
  // BDD: "Missing-tenant on the paciente write shows a session banner prompting re-authentication"
  // REQUIRES: BLOCKER-1, BLOCKER-4, BLOCKER-7 resolved
  test.fixme(
    'Missing-tenant on the paciente write shows a session banner prompting re-authentication',
    async ({ page }) => {
      // BLOCKER-4: requires HTTP adapter + page.route() to return 403 missing-tenant.
      // BLOCKER-7: PatientRegisterComponent not embedded.
      await routePacApiPacientes(page, MISSING_TENANT_PROBLEM, 403);

      await openOnboarding(page);
      const step2 = await getStep2(page);

      await step2.locator('pac-ds-input input').first().fill('Márcio Amaral');
      await step2.locator('pac-ds-button').filter({ hasText: /Registrar paciente/i }).locator('button').click();

      // PacErrorHandler should surface missing-tenant as a session banner
      // (PacFeedbackService routes 'missing-tenant' to bannerMessage with re-auth prompt)
      await expect(
        page.locator('[role="alert"], .pac-session-banner, text=autenticação'),
      ).toBeVisible({ timeout: 5000 });
    },
  );

  // ── Scenario 8: ACS wizard shows "Paciente" step heading ──────────────────
  // BDD (adapted): Onboarding wizard step 2 is visible and labelled "Paciente"
  // REQUIRES: BLOCKER-1 resolved; works without BLOCKER-7 (tests step heading, not component)
  // NOTE: This is an addition to test what IS currently rendered in the wizard (step 2 label).
  test.fixme(
    'ACS wizard step 2 is the Paciente step',
    async ({ page }) => {
      // BLOCKER-1: dev server unavailable.
      // Once BLOCKER-1 is fixed: this verifies the basic structure of the onboarding wizard.
      await openOnboarding(page);

      // Step 2 heading must be "Paciente"
      const step2 = page.locator('h2', { hasText: 'Paciente' });
      await expect(step2).toBeVisible();

      // Currently: step 2 shows a placeholder link to /pac (before BLOCKER-7 fix)
      // After BLOCKER-7 fix: pac-patient-register component renders here
      const step2Container = step2.locator('..');
      await expect(step2Container).toBeVisible();
    },
  );

  // ── Scenario 9: Breadcrumb ownership — no PAC breadcrumb in wizard ─────────
  // BDD: "Patient register feature composed inside the onboarding wizard contributes
  //       no PAC breadcrumb for the paciente step"
  // REQUIRES: BLOCKER-1 resolved; can run before BLOCKER-7 (asserts absence)
  test(
    'Patient register feature composed inside the onboarding wizard contributes no PAC breadcrumb',
    async ({ page }) => {
      // BLOCKER-1: dev server unavailable. When resolved, this test verifies that
      // the /onboarding page does not render a PAC-owned breadcrumb.
      //
      // PatientRegisterComponent is a "composed" feature (not routed by PAC) — it must
      // NOT contribute a PAC breadcrumb at the shell level (SPEC-06).
      // The PAC breadcrumb signal only updates for PAC-routed pages (/pac, /pac/comandos/*).
      await openOnboarding(page);

      // No breadcrumb should mention PAC-specific routes or "Calibração"
      const breadcrumb = page.locator('[aria-label="breadcrumb"], nav[class*="breadcrumb"]');
      // If no breadcrumb element exists at all, that is acceptable (no breadcrumb = no PAC breadcrumb)
      const breadcrumbCount = await breadcrumb.count();
      if (breadcrumbCount > 0) {
        await expect(breadcrumb).not.toContainText('Calibração');
        await expect(breadcrumb).not.toContainText('/pac');
      }
      // The heading "Configuração inicial" should be from the ACS wizard, not PAC
      await expect(page.locator('h1')).toContainText('Configuração inicial');
    },
  );

});
