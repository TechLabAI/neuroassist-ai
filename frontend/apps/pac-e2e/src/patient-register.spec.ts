/**
 * E2E spec: Onboarding — register paciente (step 2)
 * Feature: docs/architecture/frontend/modules/pac/frontend/e2e/patient-register.feature
 * PAC-FE-025: 9 scenarios (PAC P9, reduced scope)
 * Gate: A5 e2e-validate. Prototype-first (D-484), registered by fe-nx-register-module.py (D-487).
 *
 * ── ESTADO DOS BLOQUEIOS (revisto em 2026-08-25) ─────────────────────────────
 *
 *   BLOCKER-1 [dev server]: CADUCADO — a app serve normalmente.
 *
 *   BLOCKER-4 [adaptador HTTP]: RESOLVIDO — `providePac()` lê
 *     `window.__PAC_E2E_ADAPTER` atrás de `isDevMode()`. Ver fixtures/pac-stubs.ts.
 *
 *   BLOCKER-5 [RBAC]: RESOLVIDO no PAC — `PAC_ACCESS_DECISION` é providenciado em
 *     app.config.ts e traduz chave de tela → authority pelo `PAC_PERMISSION_MAP`.
 *     Falta o `PatientRegisterComponent` CONSULTAR a decisão (cenário 2).
 *
 *   BLOCKER-7 [componente não embutido]: **AINDA VALE, E É FALTA DE PRODUTO.**
 *
 *     `OnboardingWizardPageComponent`
 *     (apps/neuroassist-ai-web/src/app/pages/onboarding-wizard-page.component.ts)
 *     NÃO embute o `PatientRegisterComponent`. O passo 2 é um parágrafo estático:
 *
 *         "O registro do paciente é gerido pelo módulo PAC. [Ir ao catálogo →/pac]"
 *
 *     E ISSO FECHA UM CICLO SEM SAÍDA, que é o defeito de verdade por trás deste
 *     bloqueio: sem paciente registrado, /pac cai no `pacTenantPatientGuard`, que
 *     redireciona para /onboarding; /onboarding manda de volta para /pac. Não há
 *     NENHUMA superfície no produto por onde cadastrar o primeiro paciente — o
 *     componente que faria isso existe, está exportado no barril do PAC
 *     (`PatientRegisterComponent`) e não é montado em lugar nenhum. O item
 *     "Paciente registrado" da lista de progresso do wizard lê
 *     `pacFacade.hasPatient()`, que nunca pode virar `true` por ação de quem usa.
 *
 *     A CORREÇÃO é de três linhas, e fica em `onboarding-wizard-page.component.ts`
 *     — arquivo do shell, FORA do escopo desta tarefa (é o wizard do ACS, que
 *     outra frente está mexendo em paralelo):
 *
 *         import { PatientRegisterComponent } from '@neuroassist/pac';
 *         // imports: [..., PatientRegisterComponent]
 *         // no passo 2, no lugar do parágrafo:
 *         <pac-patient-register />
 *
 *     Os sete cenários abaixo ficam VERMELHOS até isso acontecer. Estão escritos
 *     inteiros e apontando para o DOM que o componente de fato renderiza, de modo
 *     que a correção acima os liga sem mais nenhuma edição aqui.
 *
 *   LACUNA ADICIONAL nos cenários 4, 5 e 7 (além do BLOCKER-7): a superfície de
 *     erro do PAC não chega ao registro de paciente. `PacPacienteFacade.save()`
 *     colapsa QUALQUER falha em `saveState = 'error'` e o componente mostra uma
 *     frase genérica; `PacErrorHandler` / `PacFeedbackService` — que sabem mapear
 *     os 12 ProblemDetail, inclusive `missing-tenant` para banner e `validation`
 *     para dica de campo — existem, estão exportados no barril e NÃO são
 *     injetados por componente nenhum. Ver o relatório de stubs vivos.
 */
import { test, expect, Page } from '@playwright/test';

import {
  scriptSemearSessao,
  scriptSemearSessaoCuidadora,
} from '@neuroassist/shared/auth-login/testing';
import { scriptSemAlarmeDeEmergencia } from '@neuroassist/evnt/testing';

import {
  aplicarModoHttp,
  rotaPacientes,
  PAC_BASE,
  PAGINA_SEM_PACIENTE,
  problemDetail,
} from './fixtures/pac-stubs';

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

// ── Navigation helper ─────────────────────────────────────────────────────────

/** Navigate to /onboarding and wait for the wizard to render. */
async function openOnboarding(page: Page): Promise<void> {
  await page.goto('/onboarding');
  await page.waitForSelector('h1', { timeout: 8000 });
  await expect(page.locator('h1')).toContainText('Configuração inicial');
}

/**
 * O passo 2 do wizard, localizado pelo COMPONENTE que deve morar nele.
 *
 * <p>O seletor anterior procurava a caixa pela classe utilitária
 * (`[class*="rounded-2xl"]` + texto "2 Paciente"), que casa com o parágrafo
 * placeholder tanto quanto casaria com o componente — isto é, não distinguia
 * "o passo existe" de "o passo faz alguma coisa". `pac-patient-register` é a
 * afirmação honesta: enquanto o componente não estiver montado, o localizador
 * não acha nada e o cenário reprova, que é a informação correta (BLOCKER-7).
 */
function passo2(page: Page) {
  return page.locator('pac-patient-register');
}

/** O campo de nome dentro do passo 2. */
function campoNome(page: Page) {
  return passo2(page).locator('pac-ds-input input').first();
}

/** O botão de salvar do passo 2 ("Registrar paciente" ou "Salvar paciente"). */
function botaoSalvar(page: Page) {
  return passo2(page)
    .locator('pac-ds-button')
    .filter({ hasText: /Registrar paciente|Salvar paciente/i })
    .locator('button');
}

/**
 * Modo HTTP com a empresa AINDA SEM PACIENTE.
 *
 * <p>É o estado real do onboarding: quem chega no passo 2 é justamente quem não
 * tem paciente. Com a página de pacientes devolvendo a lista semeada, o
 * componente entraria no ramo "já registrado" e nenhum dos cenários de cadastro
 * teria formulário para preencher.
 */
async function modoHttpSemPaciente(page: Page): Promise<void> {
  await aplicarModoHttp(page);
  await rotaPacientes(page, PAGINA_SEM_PACIENTE);
}

// ── Tests ─────────────────────────────────────────────────────────────────────

/**
 * Abre o assistente com o mock SEM paciente semeado.
 *
 * <p>O mock semeia "Márcio Amaral", e com paciente o componente entra no ramo
 * "já registrado" — que não tem formulário nenhum. Os cenários de CADASTRO
 * precisam partir do estado em que ainda não há paciente, que é o estado real de
 * quem acabou de receber a clínica. O hook já existia
 * (`paciente-mock.adapter.ts`), usado só pelo catálogo.
 */
async function abrirAssistenteSemPaciente(page: Page): Promise<void> {
  await page.addInitScript(() => {
    (window as unknown as Record<string, unknown>)['__PAC_E2E_NO_PATIENT'] = true;
  });
  await openOnboarding(page);
}

test.describe('Onboarding — register paciente (step 2)', () => {

  // ── Scenario 1: Register paciente unlocks command catalog step ─────────────
  // BDD: "Register a paciente on the active empresa unlocks the command catalog step"
  // VERMELHO POR BLOCKER-7 (componente não embutido no passo 2).
  test('Register a paciente on the active empresa unlocks the command catalog step', async ({ page }) => {
    await abrirAssistenteSemPaciente(page);

    await expect(campoNome(page)).toBeVisible({ timeout: 5000 });
    await campoNome(page).fill('Márcio Amaral');

    await expect(botaoSalvar(page)).toBeEnabled();
    await botaoSalvar(page).click();

    // Confirmação do próprio PatientRegisterComponent.
    await expect(passo2(page).getByText('Paciente registrado com sucesso')).toBeVisible({
      timeout: 10_000,
    });

    // E a lista de progresso do wizard, que lê `pacFacade.hasPatient()`, passa a
    // marcar o item — é isto que "unlocks the command catalog step" quer dizer.
    // O checklist do assistente registra o avanço. Endereçado por gancho estável,
    // e não por forma de DOM: o ícone é uma ligadura, então o texto do item é
    // "check_circle Paciente registrado" — o seletor anterior procurava um `div`
    // cujo texto fosse exatamente o rótulo, e esse elemento não existe.
    const itemProgresso = page.locator('[data-testid="checklist-item"][data-item="Paciente registrado"]');
    await expect(itemProgresso).toHaveAttribute('data-done', 'true');
  });

  // ── Scenario 2: Caller lacks write permission — save action disabled ────────
  // BDD: "Caller lacking paciente write permission has the onboarding paciente save action disabled"
  //
  // VERMELHO POR BLOCKER-7 **E** por uma segunda lacuna: o
  // `PatientRegisterComponent` não consulta `PAC_ACCESS_DECISION`. A chave de
  // tela já existe e já está correta (`write` → `pac.paciente.criar`, conferido
  // no RoleAuthorityPolicy); falta o componente perguntar.
  //
  // Aqui a CUIDADORA é a sessão certa — e é o único cenário do PAC em que ela é:
  // ela tem `pac.paciente.editar` e NÃO tem `pac.paciente.criar`, porque cadastrar
  // paciente ficou sendo da coordenadora (um paciente criado pela cuidadora
  // nasceria descoberto e ela não poderia se vincular a ele).
  // INALCANÇÁVEL POR DECISÃO DE PRODUTO — não é lacuna de implementação.
  //
  // O cenário pede alguém que CHEGUE ao passo do paciente e não possa salvar.
  // Não existe esse alguém: `/onboarding` exige `acs.cuidador.criar`, que só a
  // coordenadora recebe, e quem tem essa authority tem também
  // `pac.paciente.criar`. A cuidadora é barrada na rota, antes da tela.
  //
  // Isso não é descuido: cadastrar paciente é da coordenadora (decisão de
  // 2026-08-24, `docs/operacao/fluxo-e-perfis.md` §3 nota 1), justamente porque
  // um paciente criado por quem não pode vincular nasce descoberto.
  //
  // Fica registrado como `fixme` COM A RAZÃO, e não removido: se um dia existir
  // um papel que leia sem escrever, este é o cenário que passa a valer.
  test.fixme(
    'Caller lacking paciente write permission has the onboarding paciente save action disabled',
    async ({ page }) => {
      await page.addInitScript(scriptSemearSessaoCuidadora());
      await openOnboarding(page);

      await expect(passo2(page)).toBeVisible({ timeout: 5000 });
      await expect(botaoSalvar(page)).toBeDisabled();
    },
  );

  // ── Scenario 3: Blank nome keeps save disabled ────────────────────────────
  // BDD: "Blank nome keeps the onboarding paciente save action disabled"
  // VERMELHO POR BLOCKER-7 apenas — a regra já está no componente:
  //   [disabled]="!nomeInput().trim() || facade.saveState() === 'saving'"
  test('Blank nome keeps the onboarding paciente save action disabled', async ({ page }) => {
    await abrirAssistenteSemPaciente(page);

    await expect(campoNome(page)).toBeVisible({ timeout: 5000 });
    await campoNome(page).fill('');
    await expect(botaoSalvar(page)).toBeDisabled();

    await campoNome(page).fill('Ana');
    await expect(botaoSalvar(page)).toBeEnabled();

    await campoNome(page).fill('');
    await expect(botaoSalvar(page)).toBeDisabled();
  });

  // ── Scenario 4: Unknown dispositivo reference → inline validation error ────
  // BDD: "Unknown dispositivo reference rejects the paciente with an inline validation error"
  // VERMELHO POR BLOCKER-7 **E** pela superfície de erro não ligada (ver cabeçalho):
  // o componente só sabe dizer "Erro ao salvar", nunca o erro de campo.
  test('Unknown dispositivo reference rejects the paciente with an inline validation error', async ({ page }) => {
    await modoHttpSemPaciente(page);
    await page.route(`**${PAC_BASE}`, (route) =>
      route.request().method() === 'POST'
        ? route.fulfill({
            status: 400,
            contentType: 'application/problem+json',
            body: JSON.stringify(
              problemDetail('validation', 400, 'dispositivo: device reference unknown to EDGE.', {
                fieldErrors: [
                  { field: 'dispositivo', message: 'Dispositivo desconhecido no catálogo EDGE.' },
                ],
              }),
            ),
          })
        : route.fallback(),
    );

    await openOnboarding(page);
    // Falha rápido e com a mensagem certa: sem o componente montado (BLOCKER-7)
    // um `fill()` direto ficaria 30s pendurado e reprovaria por timeout, o que
    // esconde a causa atrás de "o teste demorou".
    await expect(passo2(page)).toBeVisible({ timeout: 5000 });
    await campoNome(page).fill('Márcio Amaral');
    await botaoSalvar(page).click();

    await expect(passo2(page).getByText(/Dispositivo desconhecido/i)).toBeVisible({ timeout: 10_000 });
    await expect(passo2(page).getByText('Paciente registrado com sucesso')).toHaveCount(0);
  });

  // ── Scenario 5: Unknown cuidador reference → inline validation error ────────
  // BDD: "Unknown cuidador reference rejects the paciente with an inline validation error"
  // VERMELHO pelos mesmos dois motivos do cenário 4.
  test('Unknown cuidador reference rejects the paciente with an inline validation error', async ({ page }) => {
    await modoHttpSemPaciente(page);
    await page.route(`**${PAC_BASE}`, (route) =>
      route.request().method() === 'POST'
        ? route.fulfill({
            status: 400,
            contentType: 'application/problem+json',
            body: JSON.stringify(
              problemDetail('validation', 400, 'cuidador: caregiver reference unknown to ACS.', {
                fieldErrors: [
                  { field: 'cuidador', message: 'Cuidador desconhecido no catálogo ACS.' },
                ],
              }),
            ),
          })
        : route.fallback(),
    );

    await openOnboarding(page);
    // Falha rápido e com a mensagem certa: sem o componente montado (BLOCKER-7)
    // um `fill()` direto ficaria 30s pendurado e reprovaria por timeout, o que
    // esconde a causa atrás de "o teste demorou".
    await expect(passo2(page)).toBeVisible({ timeout: 5000 });
    await campoNome(page).fill('Márcio Amaral');
    await botaoSalvar(page).click();

    await expect(passo2(page).getByText(/Cuidador desconhecido/i)).toBeVisible({ timeout: 10_000 });
    await expect(passo2(page).getByText('Paciente registrado com sucesso')).toHaveCount(0);
  });

  // ── Scenario 6: ACS unreachable — retryable message ──────────────────────
  // BDD: "ACS unreachable during the paciente write makes the caregiver retry"
  // VERMELHO POR BLOCKER-7 apenas: a frase que este cenário espera JÁ existe no
  // componente ("Erro ao salvar. Verifique sua conexão e tente novamente."). Este
  // é o cenário que a correção de três linhas liga sozinha.
  test('ACS unreachable during the paciente write makes the caregiver retry', async ({ page }) => {
    await modoHttpSemPaciente(page);
    await page.route(`**${PAC_BASE}`, (route) =>
      route.request().method() === 'POST' ? route.abort('failed') : route.fallback(),
    );

    await openOnboarding(page);
    // Falha rápido e com a mensagem certa: sem o componente montado (BLOCKER-7)
    // um `fill()` direto ficaria 30s pendurado e reprovaria por timeout, o que
    // esconde a causa atrás de "o teste demorou".
    await expect(passo2(page)).toBeVisible({ timeout: 5000 });
    await campoNome(page).fill('Márcio Amaral');
    await botaoSalvar(page).click();

    await expect(passo2(page).getByText(/Erro ao salvar/i)).toBeVisible({ timeout: 10_000 });
    await expect(passo2(page).getByText('Paciente registrado com sucesso')).toHaveCount(0);
  });

  // ── Scenario 7: Missing-tenant → session banner ────────────────────────────
  // BDD: "Missing-tenant on the paciente write shows a session banner prompting re-authentication"
  // VERMELHO POR BLOCKER-7 **E** pela superfície de erro não ligada: o
  // `PacFeedbackService` roteia 'missing-tenant' para banner de sessão, e ninguém
  // o injeta.
  test('Missing-tenant on the paciente write shows a session banner prompting re-authentication', async ({ page }) => {
    await modoHttpSemPaciente(page);
    await page.route(`**${PAC_BASE}`, (route) =>
      route.request().method() === 'POST'
        ? route.fulfill({
            status: 403,
            contentType: 'application/problem+json',
            body: JSON.stringify(
              problemDetail(
                'missing-tenant',
                403,
                'The request does not carry a valid X-Empresa-Id header.',
              ),
            ),
          })
        : route.fallback(),
    );

    await openOnboarding(page);
    // Falha rápido e com a mensagem certa: sem o componente montado (BLOCKER-7)
    // um `fill()` direto ficaria 30s pendurado e reprovaria por timeout, o que
    // esconde a causa atrás de "o teste demorou".
    await expect(passo2(page)).toBeVisible({ timeout: 5000 });
    await campoNome(page).fill('Márcio Amaral');
    await botaoSalvar(page).click();

    // Banner de sessão pedindo nova autenticação — não um "erro ao salvar"
    // genérico: a diferença importa porque a ação de quem lê é outra.
    await expect(
      page.locator('[role="alert"]').filter({ hasText: /autentic|sessão|entrar de novo/i }).first(),
    ).toBeVisible({ timeout: 10_000 });
  });

  // ── Scenario 8: ACS wizard shows "Paciente" step heading ──────────────────
  // BDD (adapted): Onboarding wizard step 2 is visible and labelled "Paciente"
  // EXECUTÁVEL: dependia só do BLOCKER-1, que caducou. Afirma a estrutura do
  // wizard (o passo existe e é o do paciente), não o componente do passo.
  test('ACS wizard step 2 is the Paciente step', async ({ page }) => {
    await openOnboarding(page);

    // Nome EXATO: `hasText` é substring, e o assistente ganhou um passo
    // "Vincular cuidadora ao paciente" — o locator solto passou a casar com os
    // dois. Um título de passo é um nome, não um pedaço de frase.
    const tituloPasso2 = page.getByRole('heading', { name: 'Paciente', exact: true });
    await expect(tituloPasso2).toBeVisible();

    // O passo 2 vem depois do passo 1 (Unidade de cuidado) e antes do 3
    // (Dispositivo edge) — a ordem é o que faz dele "o passo 2".
    await expect(page.locator('h2', { hasText: 'Unidade de cuidado' })).toBeVisible();
    await expect(page.locator('h2', { hasText: 'Dispositivo edge' })).toBeVisible();
  });

  // ── Scenario 9: Breadcrumb ownership — no PAC breadcrumb in wizard ─────────
  // BDD: "Patient register feature composed inside the onboarding wizard contributes
  //       no PAC breadcrumb for the paciente step"
  test(
    'Patient register feature composed inside the onboarding wizard contributes no PAC breadcrumb',
    async ({ page }) => {
      // PatientRegisterComponent é uma feature COMPOSTA (não roteada pelo PAC) —
      // não deve contribuir migalha de pão do PAC no nível do shell (SPEC-06).
      // A migalha do PAC só se atualiza em páginas roteadas pelo PAC (/pac, /pac/comandos/*).
      await openOnboarding(page);

      const migalha = page.locator('[aria-label="breadcrumb"], nav[class*="breadcrumb"]');
      // Nenhuma migalha é resposta aceitável: sem migalha, não há migalha do PAC.
      if ((await migalha.count()) > 0) {
        await expect(migalha).not.toContainText('Calibração');
        await expect(migalha).not.toContainText('/pac');
      }
      // O título é do wizard do ACS, não do PAC.
      await expect(page.locator('h1')).toContainText('Configuração inicial');
    },
  );

});
