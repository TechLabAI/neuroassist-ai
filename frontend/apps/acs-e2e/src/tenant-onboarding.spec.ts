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
 * ESTADO DOS BLOQUEIOS: NENHUM. Os cinco vermelhos deliberados desta suíte
 * foram fechados — e dois deles estavam medindo a coisa errada.
 *
 *   2, 3, 4 — o assistente passou a ter TRÊS estados de empresa (sem empresa ·
 *             provisionando · ativa) e afordância de retentativa. O mock do
 *             tenant ganhou as bandeiras que faltavam: até aqui ele devolvia
 *             'active' incondicionalmente, então o estado intermediário — que o
 *             backend produz de verdade, por saga, com 202 — não tinha como
 *             aparecer em teste nenhum.
 *
 *   5       — a PREMISSA estava errada e foi reescrita. O cenário fazia uma
 *             CUIDADORA chegar a /onboarding, o que é impossível: a rota exige
 *             `acs.cuidador.criar` no canActivate e a cuidadora não a tem — ela
 *             cairia no guard, não no botão. E o critério certo não é o papel
 *             dela: criar empresa exige `acs.tenant.provisionar`, que o
 *             RoleAuthorityPolicy não concede a papel humano NENHUM (E2E-029 a
 *             moveu para o cliente de serviço `platform-ops`). Quem chega à tela
 *             e vê o botão desabilitado é a COORDENADORA.
 *
 *   6       — o passo de vínculo existe. Era `TODO[FE-ACS-021]` num método
 *             (`onboardingWizardComposition()`) que não tinha chamador; agora o
 *             assistente compõe a `PatientLinkageComponent` por slot, como já
 *             fazia com o pareamento do EDGE.
 *
 * QUEM É O USUÁRIO DE CADA CENÁRIO (não é detalhe de teste — é o produto):
 *   1-4  condutor de plataforma  → exercita PROVISIONAR empresa (Passo 0 do
 *        tutorial: "a clínica é criada pela equipe técnica")
 *   5-8  coordenadora            → é quem usa o assistente no dia a dia
 *
 * MOCK SEED STATE:
 *   - _tenants: Família Amaral (tenant-familia-amaral, active)
 *   - create() devolve uma empresa ATIVA (201) — salvo com a bandeira
 *     `empresaProvisionando`, que a faz nascer em provisionamento (202)
 *   - retryProvisioning() RESOLVE — salvo com a bandeira `retryNaoResolve`
 */
import { test, expect, Page } from '@playwright/test';

import { scriptSemearSessao } from '@neuroassist/shared/auth-login/testing';
import { scriptSemAlarmeDeEmergencia } from '@neuroassist/evnt/testing';
import { ACS_E2E_FLAGS, scriptLigarBandeirasAcs } from '@neuroassist/acs/testing';
import { scriptSemearSessaoCondutorPlataforma } from './sessao-condutor-plataforma';

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
    // Provisionar empresa é do condutor de plataforma. Com a sessão de
    // coordenadora este cenário passava a medir a AUSÊNCIA da trava de
    // permissão, e não o caminho feliz do provisionamento.
    await page.addInitScript(scriptSemearSessaoCondutorPlataforma());
    await openOnboarding(page);

    // Wizard heading must be visible.
    // Use getByRole to avoid strict mode violation: DevicePairingPageComponent (rendered
    // inside step 3 via NgComponentOutlet) also contributes a <h1>Parear dispositivo</h1>,
    // so page.locator('h1') resolves to 2 elements and Playwright rejects it.
    await expect(page.getByRole('heading', { name: /Configuração inicial/i })).toBeVisible();

    // Step 1: tenant setup — inline input with placeholder "Nome da família ou clínica"
    const tenantInput = page.getByPlaceholder(/nome da família ou clínica/i);
    await expect(tenantInput).toBeVisible();

    await tenantInput.fill('Família Teste E2E');

    const criarBtn = page.getByRole('button', { name: /criar/i });
    await expect(criarBtn).toBeEnabled();
    await criarBtn.click();

    // Mock create() resolve em 800ms com provisioningStatus 'active' (201).
    // A insígnia agora exige isActive — não a mera existência do registro.
    await expect(page.getByTestId('tenant-active')).toBeVisible({ timeout: 4000 });
    await expect(page.getByText('Família Teste E2E')).toBeVisible();
    await expect(page.getByTestId('tenant-provisioning')).toBeHidden();

    // Step 1 section should no longer show the input (a empresa está pronta).
    await expect(tenantInput).not.toBeVisible();
  });

  // ── Scenario 2: Provisioning pending state ───────────────────────────────
  // BDD: "Provisioning that is not yet confirmed shows a provisioning empresa state"
  test('Provisioning that is not yet confirmed shows a provisioning empresa state', async ({ page }) => {
    await page.addInitScript(scriptSemearSessaoCondutorPlataforma());
    // Sem esta bandeira o mock devolvia 'active' e o estado nem existia.
    await page.addInitScript(scriptLigarBandeirasAcs(ACS_E2E_FLAGS.empresaProvisionando));
    await openOnboarding(page);
    await page.getByPlaceholder(/nome da família ou clínica/i).fill('Pending Corp');
    await page.getByRole('button', { name: /criar/i }).click();

    // ATENÇÃO à asserção anterior, que era um FALSO VERDE esperando para
    // acontecer: `getByText(/provisionando|aguardando/i)` casava com
    // "Aguardando o agente reclamar o código…" do DevicePairingPageComponent, que
    // o passo 3 do wizard monta via NgComponentOutlet. Nada a ver com
    // provisionamento de empresa — o cenário teria passado sem a tela existir.
    //
    // Endereçamos um testid próprio do passo de empresa.
    await expect(page.getByTestId('tenant-provisioning')).toBeVisible({ timeout: 4000 });

    // E o inverso: uma empresa em provisionamento NÃO pode aparecer como pronta.
    // É o defeito que a tela tinha — insígnia verde ligada a `!!tenant()`.
    await expect(page.getByTestId('tenant-active')).toBeHidden();
  });

  // ── Scenario 3: Provisioning retry succeeds ───────────────────────────────
  // BDD: "A provisioning retry that succeeds writes the active empresa context"
  //
  // O corpo anterior clicava em "tentar novamente" logo após abrir a página, sem
  // empresa nenhuma registrada — não havia o que retentar. Agora o cenário
  // constrói o estado que a retentativa pressupõe.
  test('A provisioning retry that succeeds writes the active empresa context', async ({ page }) => {
    await page.addInitScript(scriptSemearSessaoCondutorPlataforma());
    await page.addInitScript(scriptLigarBandeirasAcs(ACS_E2E_FLAGS.empresaProvisionando));
    await openOnboarding(page);
    await page.getByPlaceholder(/nome da família ou clínica/i).fill('Retry Ok Corp');
    await page.getByRole('button', { name: /criar/i }).click();
    await expect(page.getByTestId('tenant-provisioning')).toBeVisible({ timeout: 4000 });

    // Sem a bandeira `retryNaoResolve`, a retentativa resolve para 'active'.
    await page.getByRole('button', { name: /tentar novamente/i }).click();

    // .first() porque o estado de sucesso fala DUAS vezes ('— empresa ativa' na
    // insignia e 'Empresa confirmada pelo backend' no corpo) e o modo estrito
    // recusa dois casamentos. As duas sao o mesmo estado; a assercao seguinte
    // fixa qual.
    await expect(page.getByText(/ativa|confirmada/i).first()).toBeVisible({ timeout: 4000 });
    // Escopado, para que a faixa "ainda não confirmada" — que também casa com
    // /confirmada/i — não possa satisfazer este cenário por acidente.
    await expect(page.getByTestId('tenant-active')).toBeVisible();
    await expect(page.getByTestId('tenant-provisioning')).toBeHidden();
  });

  // ── Scenario 4: Provisioning retry still not active ──────────────────────
  // BDD: "A provisioning retry that is still not active shows a retryable empresa banner"
  test('A provisioning retry that is still not active shows a retryable empresa banner', async ({ page }) => {
    await page.addInitScript(scriptSemearSessaoCondutorPlataforma());
    await page.addInitScript(
      scriptLigarBandeirasAcs(ACS_E2E_FLAGS.empresaProvisionando, ACS_E2E_FLAGS.retryNaoResolve),
    );
    await openOnboarding(page);
    // First attempt: still provisioning
    await page.getByPlaceholder(/nome da família ou clínica/i).fill('Retry Corp');
    await page.getByRole('button', { name: /criar/i }).click();
    await expect(page.getByTestId('tenant-provisioning')).toBeVisible({ timeout: 4000 });

    // A faixa NÃO pode existir antes de a pessoa ter tentado de novo: a espera
    // normal da saga não é falha.
    await expect(page.getByTestId('tenant-provisioning-failed')).toBeHidden();

    await page.getByRole('button', { name: /tentar novamente/i }).click();

    // Retryable banner still showing
    await expect(page.getByText(/ainda não confirmada|tentar novamente/i).first()).toBeVisible({ timeout: 5000 });
    await expect(page.getByTestId('tenant-provisioning-failed')).toBeVisible();
    await expect(page.getByTestId('tenant-active')).toBeHidden();
  });

  // ── Scenario 5: Criar empresa disabled without permission ─────────────────
  // BDD: "The criar empresa action is disabled without tenant write permission"
  //
  // A PREMISSA ANTERIOR ERA IMPOSSÍVEL, e o comentário que a defendia estava
  // desatualizado em dois pontos:
  //
  //   1. dizia que "/onboarding não tem canActivate nenhum". Tem:
  //      `[sessaoRequeridaGuard, acsCaregiverWriteGuard]`. Logo a CUIDADORA, que
  //      não possui `acs.cuidador.criar`, nunca chega a esta tela — o cenário
  //      teria falhado no guard, longe do botão que queria observar.
  //   2. a nota final ("a authority a exigir precisa ser decidida") já está
  //      decidida no backend: `TenantController:45` exige
  //      `acs.tenant.provisionar`, e o RoleAuthorityPolicy não a dá a papel
  //      humano nenhum — E2E-029 tirou-a do COORDENADOR_CLINICA e a pôs no
  //      cliente de serviço `platform-ops`.
  //
  // Portanto quem prova a regra é a COORDENADORA: ela É o único papel humano que
  // chega ao assistente, e mesmo assim não pode criar a empresa.
  test('The criar empresa action is disabled without tenant write permission', async ({ page }) => {
    // Sessão de coordenadora — a semeada por padrão no beforeEach.
    await openOnboarding(page);

    // PRECISA preencher o nome antes de afirmar. Sem isto o cenário era um FALSO
    // VERDE: o botão é `[disabled]="… || !tenantNome().trim() || …"`, ou seja,
    // fica desabilitado por campo vazio — nada a ver com permissão.
    await page.getByPlaceholder(/nome da família ou clínica/i).fill('Empresa Proibida');

    await expect(
      page.getByRole('button', { name: /criar/i }),
      'a coordenadora não tem acs.tenant.provisionar — criar empresa é do administrador de plataforma',
    ).toBeDisabled();

    // E a tela DIZ por quê, em vez de apenas apagar o botão.
    await expect(page.getByTestId('tenant-write-denied')).toBeVisible();
  });

  // ── Scenario 6: Registering caregiver + link covers the paciente ──────────
  // BDD: "Registering the primary caregiver and linking it covers the paciente"
  //
  // É o Passo 3 do tutorial ("a coordenadora vincula cuidadoras ao paciente"),
  // que não tinha superfície alguma no produto.
  test('Registering the primary caregiver and linking it covers the paciente', async ({ page }) => {
    await openOnboarding(page);

    await expect(page.getByTestId('caregiver-link-step')).toBeVisible({ timeout: 5000 });

    // O passo é a superfície REAL de vínculo, não um invólucro com testid: o
    // que prova isso é o conteúdo próprio da PatientLinkageComponent — o
    // distintivo de cobertura, que ela deriva do elenco (ADR-005).
    await expect(page.getByTestId('coverage-badge')).toBeVisible({ timeout: 5000 });

    // E ela está COMPOSTA, não montada como página: não contribui o próprio <h1>.
    await expect(page.getByRole('heading', { level: 1, name: /Vínculos do paciente/i })).toHaveCount(0);
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
    // 'exact: true' porque o nome acessivel casa por SUBSTRING: desde que o
    // passo 6 (vinculo) existe, ha um segundo <h2> — 'Vincular cuidadora ao
    // paciente' — que tambem contem 'paciente', e o modo estrito recusa dois.
    await expect(page.getByRole('heading', { name: 'Paciente', exact: true })).toBeVisible();
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
