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
 * ESTADO DOS BLOQUEIOS (revisto — nenhum `test.fixme` restante nesta suíte).
 *
 * NENHUM dos cinco bloqueios desta suíte caducou, e os cinco têm a MESMA causa:
 * o que falta está em `apps/neuroassist-ai-web/src/app/` — o
 * OnboardingWizardPageComponent e a declaração da rota /onboarding —, fora do
 * escopo desta tarefa. Não é falta de gancho de teste: as facades do ACS já
 * expõem o que a tela precisaria desenhar.
 *
 *   2, 3, 4 — `AcsTenantFacade.provisioning()` e `retryProvisioning()` existem e
 *             estão corretos; o wizard não desenha estado 'provisionando' nem
 *             oferece botão de retentativa.
 *   5       — a rota /onboarding não tem `canActivate` algum. Buraco real e
 *             gêmeo do que /acs tinha (fechado no cenário 25 do roster): o menu
 *             esconde o item, a URL entra assim mesmo.
 *   6       — o passo 5 do wizard é placeholder (TODO[FE-ACS-021]); atravessa
 *             ACS + PAC + rota de vínculo.
 *
 * Os cenários EXECUTAM e ficam VERMELHOS com a asserção real e a mensagem do que
 * falta. Não troque por `skip`.
 *
 * MOCK SEED STATE:
 *   - _tenants: Família Amaral (tenant-familia-amaral, active)
 *   - Mock create() returns a new active tenant with the provided nome
 */
import { test, expect, Page } from '@playwright/test';

import {
  scriptSemearSessao,
  scriptSemearSessaoCuidadora,
} from '@neuroassist/shared/auth-login/testing';
import { scriptSemAlarmeDeEmergencia } from '@neuroassist/evnt/testing';

// Estado de partida da suíte. Ambos via `addInitScript`, que roda antes de
// qualquer script da página: o AuthSessionStore lê o sessionStorage no construtor
// e o mock do EVNT lê a bandeira na primeira chamada, então semear depois do goto
// seria tarde demais nos dois casos.
//
//   1. sessão — todas as rotas do shell passam a exigir login; sem isto a suíte
//      cai em /login;
//   2. sem emergência ativa — o mock do EVNT semeia uma, e o alarme de emergência
//      preempta a tela inteira (é o comportamento certo do produto, e a suíte do
//      EVNT existe para prová-lo). Sobre estas telas ele só contribui um segundo
//      <h1> e um alertdialog por cima do conteúdo.
test.beforeEach(async ({ page }) => {
  await page.addInitScript(scriptSemearSessao());
  await page.addInitScript(scriptSemAlarmeDeEmergencia());
});

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
  // BLOQUEIO AINDA VALE, e a metade que importa está FORA do alcance desta
  // mudança. VERMELHO DELIBERADO.
  //
  // A metade fácil é do ACS e seria uma bandeira no `AcsTenantMockAdapter` — o
  // mesmo formato de `ACS_E2E_FLAGS`. Não foi acrescentada de propósito: seria
  // superfície que ninguém lê, porque a metade que falta de verdade é a TELA.
  //
  // `AcsTenantFacade` JÁ expõe o sinal `provisioning()` (posto em `create()`
  // quando o serviço devolve 202) e ninguém o desenha: o
  // `OnboardingWizardPageComponent` só tem dois estados no passo 1 — o input e o
  // nome pronto. Esse componente vive em
  // `apps/neuroassist-ai-web/src/app/pages/onboarding-wizard-page.component.ts`,
  // fora do escopo desta tarefa (há outro agente no PAC em paralelo).
  test('Provisioning that is not yet confirmed shows a provisioning empresa state', async ({ page }) => {
    await openOnboarding(page);
    await page.getByPlaceholder(/nome da família ou clínica/i).fill('Pending Corp');
    await page.getByRole('button', { name: /criar/i }).click();

    // ATENÇÃO à asserção anterior, que era um FALSO VERDE esperando para
    // acontecer: `getByText(/provisionando|aguardando/i)` casava com
    // "Aguardando o agente reclamar o código…" do DevicePairingPageComponent, que
    // o passo 3 do wizard monta via NgComponentOutlet. Nada a ver com
    // provisionamento de empresa — o cenário teria passado sem a tela existir.
    //
    // Endereçamos um testid próprio do passo de empresa. FALHA AQUI porque essa
    // superfície não existe.
    await expect(
      page.getByTestId('tenant-provisioning'),
      'o wizard não desenha o estado provisionando; AcsTenantFacade.provisioning() existe e ninguém o lê',
    ).toBeVisible({ timeout: 4000 });
  });

  // ── Scenario 3: Provisioning retry succeeds ───────────────────────────────
  // BDD: "A provisioning retry that succeeds writes the active empresa context"
  // BLOQUEIO AINDA VALE — falta TELA, não mock. VERMELHO DELIBERADO.
  //
  // `AcsTenantFacade.retryProvisioning()` existe e está correto (escreve o
  // contexto de empresa quando resolve para 'active'). O que não existe é botão
  // algum que o chame: o template do wizard não tem afordância de retentativa.
  // O wizard está em apps/neuroassist-ai-web/src/app/pages/ — fora do escopo.
  test('A provisioning retry that succeeds writes the active empresa context', async ({ page }) => {
    await openOnboarding(page);
    // Click retry (UI not yet implemented)
    await page.getByRole('button', { name: /tentar novamente/i }).click();
    await expect(page.getByText(/ativa|confirmada/i)).toBeVisible({ timeout: 3000 });
  });

  // ── Scenario 4: Provisioning retry still not active ──────────────────────
  // BDD: "A provisioning retry that is still not active shows a retryable empresa banner"
  // BLOQUEIO AINDA VALE — soma dos dois anteriores. VERMELHO DELIBERADO.
  // Precisa do estado 'provisioning' desenhado (cenário 2) E do botão de
  // retentativa (cenário 3). Ambos no wizard, fora do escopo.
  test('A provisioning retry that is still not active shows a retryable empresa banner', async ({ page }) => {
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
  // BLOQUEIO AINDA VALE, e o buraco é REAL. VERMELHO DELIBERADO — fora do escopo.
  //
  // O menu já esconde "Configuração inicial" de quem não tem `acs.cuidador.criar`
  // (shell/menu-trabalho.ts, §7: cadastrar é operação de coordenadora), mas a
  // ROTA /onboarding não tem `canActivate` nenhum — é o mesmo buraco que /acs
  // tinha e que foi fechado no cenário 25 do roster: esconder é ergonomia, quem
  // tranca é o guard. Aqui a cuidadora ainda entra digitando a URL.
  //
  // Não fechei porque a rota é declarada em
  // `apps/neuroassist-ai-web/src/app/app.routes.ts`, fora do escopo desta tarefa
  // (há outro agente no PAC em paralelo). É o item mais fácil e mais urgente da
  // lista de vermelhos.
  //
  // Nota sobre o critério: criar EMPRESA é do administrador de plataforma, não da
  // coordenadora (fluxo-e-perfis.md §1) — nenhum papel humano com tela deveria
  // ver este passo 1. A authority a exigir precisa ser decidida junto com isso.
  test('The criar empresa action is disabled without tenant write permission', async ({ page }) => {
    await page.addInitScript(scriptSemearSessaoCuidadora());
    await openOnboarding(page);

    // PRECISA preencher o nome antes de afirmar. Sem isto o cenário era um FALSO
    // VERDE: o botão é `[disabled]="!tenantNome().trim() || loading()"`, ou seja,
    // fica desabilitado por campo vazio — nada a ver com permissão. Passaria com
    // qualquer sessão, inclusive a da coordenadora.
    await page.getByPlaceholder(/nome da família ou clínica/i).fill('Empresa Proibida');

    // FALHA AQUI: sem guard na rota, a cuidadora chega ao wizard e o botão Criar
    // fica vivo e habilitado assim que há texto.
    await expect(
      page.getByRole('button', { name: /criar/i }),
      'a rota /onboarding não tem canActivate — a cuidadora entra pela URL e provisiona empresa',
    ).toBeDisabled();
  });

  // ── Scenario 6: Registering caregiver + link covers the paciente ──────────
  // BDD: "Registering the primary caregiver and linking it covers the paciente"
  // BLOQUEIO AINDA VALE. VERMELHO DELIBERADO — e aqui o marcador estava certo.
  //
  // O cenário atravessa três módulos (ACS cria empresa e convida, PAC cadastra
  // paciente, ACS vincula) e termina noutra rota. Além do tamanho, o passo 5 do
  // wizard é hoje um PLACEHOLDER: `AcsShellIntegrationService.onboardingWizardComposition()`
  // devolve `TenantSetupComponent` no lugar do passo de vínculo, com
  // `TODO[FE-ACS-021]: replace step 5 placeholder with real CaregiverLinkStepComponent`.
  // O componente do passo não existe — não há o que percorrer.
  //
  // Além disso PAC está fora do escopo desta tarefa (outro agente em paralelo).
  test('Registering the primary caregiver and linking it covers the paciente', async ({ page }) => {
    await openOnboarding(page);
    // FALHA AQUI: o passo 5 deveria ser a tela de vínculo do cuidador; hoje é o
    // TenantSetupComponent remontado.
    await expect(
      page.getByTestId('caregiver-link-step'),
      'passo 5 do wizard é placeholder (TODO[FE-ACS-021]) — CaregiverLinkStepComponent não existe',
    ).toBeVisible({ timeout: 4000 });
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
    // O passo 2 deixou de ser um parágrafo estático ("O registro do paciente é
    // gerido pelo módulo PAC" + link para /pac) e passou a compor o
    // `PatientRegisterComponent` do PAC de verdade. A asserção acompanha: o que
    // prova a composição agora é a superfície de cadastro estar montada aqui, e
    // não uma frase avisando que ela mora noutro lugar.
    //
    // (Aquele parágrafo era a ponta visível de um ciclo sem saída: /pac sem
    // paciente cai no pacTenantPatientGuard → /onboarding → link → /pac. Não
    // havia como cadastrar o primeiro paciente em lugar nenhum do produto.)
    await expect(page.locator('pac-patient-register')).toBeVisible();

    // Step 3: Dispositivo edge — must be visible at the wizard level
    await expect(page.getByText('Dispositivo edge')).toBeVisible();

    // Step 5: Convidar cuidadora — ACS owns this step
    await expect(page.getByText('Convidar cuidadora')).toBeVisible();

    // All 5 step numbers must be rendered (step indicators 1-5)
    const stepNumbers = page.locator('.rounded-full').filter({ hasText: /^[1-5]$/ });
    await expect(stepNumbers).toHaveCount(5);
  });

});
