/**
 * E2E spec: Tenant onboarding — provision empresa and register primary caregiver
 * Feature: frontend/e2e/tenant-onboarding.feature
 * 8 scenarios — ACS P7 implementation
 * Gate: A5 e2e-validate. Prototype-first (D-484).
 *
 * ROUTE: /onboarding (OnboardingWizardPageComponent — scope:web)
 * This spec targets the onboarding wizard page, not AcsTenantSetupComponent directly.
 * TenantSetupComponent data-testid attributes are NOT available here — the wizard
 * has its own inline UI for step 1 (tenant name input + Criar button).
 *
 * INFRASTRUCTURE BLOCKERS:
 *
 *   BLOCKER-MOCK-ADAPTER [scenarios 2, 3, 4]:
 *     AcsTenantMockAdapter.create() always returns provisioningStatus: 'active' immediately
 *     (800ms mock timer). Cannot simulate 'provisioning' or 'failed' states.
 *     retryProvisioning() also always returns 'active'.
 *     FIX: E2E_ADAPTER=http + backend stub returning provisioning status on first call.
 *
 *   BLOCKER-RBAC [scenario 5]:
 *     No real access control on the onboarding wizard (no guard on /onboarding route).
 *
 *   BLOCKER-DATA [scenario 6]:
 *     Registering a primary caregiver and verifying it covers the patient requires:
 *     (a) completing steps 1-5 of the wizard + (b) navigating to /acs/pacientes/:id/vinculos.
 *     Too many dependencies for a single e2e scenario in mock mode.
 *
 * MOCK SEED STATE:
 *   - _tenants: Família Amaral (tenant-familia-amaral, active)
 *   - Mock create() returns a new active tenant with the provided nome
 *
 * SCENARIOS EXECUTABLE with mock adapter (3 real tests): 1, 7, 8
 */
import { test, expect, Page } from '@playwright/test';

// ── Navigation helper ────────────────────────────────────────────────────────

/**
 * Navigate to /onboarding and wait for the wizard page to mount.
 */
async function openOnboarding(page: Page): Promise<void> {
  await page.goto('/onboarding');
  await page.waitForSelector('h1', { timeout: 5000 });
}

// ── Tests ────────────────────────────────────────────────────────────────────

test.describe('Tenant onboarding — provision empresa and register primary caregiver', () => {

  // ── Scenario 1: Provisioning active empresa writes the empresa context ─────
  // BDD: "Provisioning an empresa that returns active writes the active empresa context"
  test('Provisioning an empresa that returns active writes the active empresa context', async ({ page }) => {
    await openOnboarding(page);

    // Wizard heading must be visible.
    // Use getByRole to avoid strict mode violation: DevicePairingPageComponent (rendered
    // inside step 3 via NgComponentOutlet) also contributes a <h1>Parear dispositivo</h1>,
    // so page.locator('h1') resolves to 2 elements and Playwright rejects it.
    await expect(page.getByRole('heading', { name: /Configuração inicial/i })).toBeVisible();

    // Step 1: tenant setup — inline input with placeholder "Nome da família ou clínica"
    const tenantInput = page.getByPlaceholder(/nome da família ou clínica/i);
    await expect(tenantInput).toBeVisible();

    // Fill tenant name
    await tenantInput.fill('Família Teste E2E');

    // Click Criar button
    const criarBtn = page.getByRole('button', { name: /criar/i });
    await expect(criarBtn).toBeEnabled();
    await criarBtn.click();

    // Mock create() resolves after 800ms with provisioningStatus: 'active'
    // The wizard template: when tenantFacade.tenant() is truthy, shows the tenant name in step 1
    await expect(page.getByText('Família Teste E2E')).toBeVisible({ timeout: 3000 });

    // Step 1 section should no longer show the input (tenantFacade.tenant() is now set)
    await expect(tenantInput).not.toBeVisible();
  });

  // ── Scenario 2: Provisioning pending state ───────────────────────────────
  // BDD: "Provisioning that is not yet confirmed shows a provisioning empresa state"
  // REQUIRES: BLOCKER-MOCK-ADAPTER
  test.fixme('Provisioning that is not yet confirmed shows a provisioning empresa state', async ({ page }) => {
    // BLOCKER-MOCK-ADAPTER: AcsTenantMockAdapter.create() always returns provisioningStatus: 'active'.
    // The OnboardingWizardPageComponent does not currently render a "provisioning" state for the
    // tenant step (it only shows the input or the success state).
    // FIX: (a) E2E_ADAPTER=http + backend returns provisioningStatus: 'provisioning' on POST, and
    //      (b) the wizard is updated to render a pending banner when status === 'provisioning'.
    await openOnboarding(page);
    await page.getByPlaceholder(/nome da família ou clínica/i).fill('Pending Corp');
    await page.getByRole('button', { name: /criar/i }).click();
    // Expect a "provisionando" or "aguardando" state indicator
    await expect(page.getByText(/provisionando|aguardando/i)).toBeVisible({ timeout: 3000 });
  });

  // ── Scenario 3: Provisioning retry succeeds ───────────────────────────────
  // BDD: "A provisioning retry that succeeds writes the active empresa context"
  // REQUIRES: BLOCKER-MOCK-ADAPTER
  test.fixme('A provisioning retry that succeeds writes the active empresa context', async ({ page }) => {
    // BLOCKER-MOCK-ADAPTER: retryProvisioning() in the mock always returns 'active' immediately.
    // The wizard does not expose a retry button in its current template (no retry UI for step 1).
    // FIX: (a) implement retry UI in the wizard, (b) E2E_ADAPTER=http + backend returns 'active'
    //      on retry after initial 'provisioning' response.
    await openOnboarding(page);
    // Click retry (UI not yet implemented)
    await page.getByRole('button', { name: /tentar novamente/i }).click();
    await expect(page.getByText(/ativa|confirmada/i)).toBeVisible({ timeout: 3000 });
  });

  // ── Scenario 4: Provisioning retry still not active ──────────────────────
  // BDD: "A provisioning retry that is still not active shows a retryable empresa banner"
  // REQUIRES: BLOCKER-MOCK-ADAPTER
  test.fixme('A provisioning retry that is still not active shows a retryable empresa banner', async ({ page }) => {
    // BLOCKER-MOCK-ADAPTER: both create() and retryProvisioning() return 'active' immediately.
    // A 'provisioning' → retry → still 'provisioning' flow requires HTTP adapter with controlled responses.
    await openOnboarding(page);
    // First attempt: still provisioning
    await page.getByPlaceholder(/nome da família ou clínica/i).fill('Retry Corp');
    await page.getByRole('button', { name: /criar/i }).click();
    await page.getByRole('button', { name: /tentar novamente/i }).click();
    // Retryable banner still showing
    await expect(page.getByText(/ainda não confirmada|tentar novamente/i)).toBeVisible({ timeout: 5000 });
  });

  // ── Scenario 5: Criar empresa disabled without permission ─────────────────
  // BDD: "The criar empresa action is disabled without tenant write permission"
  // REQUIRES: BLOCKER-RBAC
  test.fixme('The criar empresa action is disabled without tenant write permission', async ({ page }) => {
    // BLOCKER-RBAC: /onboarding has no canActivate guard. The wizard does not check
    // ACS_ACCESS_DECISION before rendering the Criar button.
    // FIX: add an access check to the onboarding route or wizard component for tenant write permission.
    await openOnboarding(page);
    // Without permission: Criar button must be disabled or hidden
    await expect(page.getByRole('button', { name: /criar/i })).toBeDisabled();
  });

  // ── Scenario 6: Registering caregiver + link covers the paciente ──────────
  // BDD: "Registering the primary caregiver and linking it covers the paciente"
  // REQUIRES: BLOCKER-DATA (multi-step cross-module flow)
  test.fixme('Registering the primary caregiver and linking it covers the paciente', async ({ page }) => {
    // BLOCKER-DATA: This scenario requires:
    //   (a) Step 1: create tenant (800ms) → active
    //   (b) Step 2: navigate to PAC to register a patient
    //   (c) Step 5: invite caregiver via ACS
    //   (d) Navigate to /acs/pacientes/:id/vinculos and verify coverage badge
    // This multi-module flow is too complex for a single mock-adapter e2e test.
    // Deferred to P9 final e2e audit or integration test with real backend.
    await openOnboarding(page);
    // ... implementation pending cross-module integration test infrastructure
  });

  // ── Scenario 7: Wizard contributes no ACS breadcrumb ─────────────────────
  // BDD: "The composed tenant setup and caregiver link steps contribute no ACS breadcrumb"
  test('The composed tenant setup and caregiver link steps contribute no ACS breadcrumb', async ({ page }) => {
    await openOnboarding(page);

    // The onboarding wizard is outside the AppShellComponent (separate route, no shell layout).
    // No sidebar, no breadcrumb navigation bar, no <nav aria-label="breadcrumb"> should be present.
    const breadcrumb = page.locator('[aria-label="breadcrumb"], .breadcrumb, nav ol');
    await expect(breadcrumb).toHaveCount(0);

    // Also confirm the page is not inside the shell (no sidebar)
    await expect(page.locator('aside')).toHaveCount(0);
  });

  // ── Scenario 8: PAC and EDGE steps composed at shell page level ───────────
  // BDD: "The PAC and EDGE onboarding steps are composed only at the shell page level"
  test('The PAC and EDGE onboarding steps are composed only at the shell page level', async ({ page }) => {
    await openOnboarding(page);

    // The wizard page renders 5 steps. PAC contributes step 2 (Paciente) and
    // EDGE contributes step 3 (Dispositivo edge) — composed as labels/indicators.
    // These are scope:web level compositions (scope:acs wizard orchestrates scope:pac + scope:edge).

    // Step 2: Paciente — must be visible at the wizard level.
    // Use getByRole('heading') to avoid strict mode violation: getByText('Paciente') with
    // exact:false (default, case-insensitive) matches 4 elements — the h2 heading, the step 2
    // paragraph ("O registro do paciente…"), a DevicePairingPageComponent paragraph
    // ("Conecte o agente edge ao paciente."), and the checklist span "Paciente registrado".
    await expect(page.getByRole('heading', { name: 'Paciente' })).toBeVisible();
    await expect(page.getByText(/registro do paciente|módulo PAC/i)).toBeVisible();

    // Step 3: Dispositivo edge — must be visible at the wizard level
    await expect(page.getByText('Dispositivo edge')).toBeVisible();

    // Step 5: Convidar cuidadora — ACS owns this step
    await expect(page.getByText('Convidar cuidadora')).toBeVisible();

    // All 5 step numbers must be rendered (step indicators 1-5)
    const stepNumbers = page.locator('.rounded-full').filter({ hasText: /^[1-5]$/ });
    await expect(stepNumbers).toHaveCount(5);
  });

});
