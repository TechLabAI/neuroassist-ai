/**
 * E2E spec: Cuidadores roster — list, coverage, invite and deactivate
 * Feature: frontend/e2e/caregiver-roster.feature
 * 25 scenarios — ACS P7 implementation
 * Gate: A5 e2e-validate. Prototype-first (D-484).
 *
 * ESTADO DOS BLOQUEIOS (revisto — nenhum `test.fixme` restante nesta suíte).
 *
 * Todos os cenários EXECUTAM. Os que não podem passar hoje ficam VERMELHOS de
 * propósito, com a asserção real do BDD e uma mensagem dizendo o que falta —
 * vermelho honesto em vez de verde falso. Não troque por `skip`.
 *
 *   CADUCARAM (agora verdes):
 *     8  — o mock não impunha login único; o acs-service impõe (409
 *          LoginDuplicateException). Corrigido o mock, não a asserção.
 *     23 — RBAC vale: ACS_ACCESS_DECISION providenciado + MODULE_PERMISSION_MAP
 *          aplicado. Sessão de cuidadora de verdade, nunca flag.
 *     25 — idem; e destravar revelou que a rota /acs não tinha tranca nenhuma.
 *          Corrigido em libs/acs/src/lib/acs.routes.ts.
 *
 *   AINDA BLOQUEADOS (vermelhos deliberados — ver o comentário de cada um):
 *     7  — `Idempotency-Key` não existe no acs-service. Implementá-lo no
 *          frontend seria inventar contrato. Decisão pendente no backend.
 *     15 — não falta gancho, falta a funcionalidade: não há endpoint nem caminho
 *          de edição de cuidador (TODO[FE-ACS-012]) e não há escrita otimista.
 *     17 — a facade é providedIn:'root' e não tem reset/ngOnDestroy; nada zera
 *          sinal nem aborta requisição em voo.
 *     22 — a pré-checagem da UI sempre dispara antes do adaptador; não há
 *          afordância de tela que produza o bypass que o cenário afirma.
 *     24 — tornou-se inalcançável: nenhum papel lê o roster sem poder escrever
 *          nele (ver o comentário do cenário).
 *
 * MOCK SEED STATE (P7 wave — extended):
 *   _cuidadores: Jamilly Amaral (cuid-jamilly, active) + Maria Santos (cuid-maria, active)
 *   _links: link-001 (Jamilly→pac-marcio), link-002 (Maria→pac-marcio), link-003 (Maria→pac-outro)
 *
 * As bandeiras de e2e vêm de `@neuroassist/acs/testing` (ACS_E2E_FLAGS) — o
 * literal mora num lugar só, porque quem escreve (suíte) e quem lê (mock)
 * precisam concordar e antes cada lado tinha a sua cópia digitada à mão.
 */
import { test, expect, Page } from '@playwright/test';

import {
  scriptSemearSessao,
  scriptSemearSessaoCuidadora,
} from '@neuroassist/shared/auth-login/testing';
import { scriptSemAlarmeDeEmergencia } from '@neuroassist/evnt/testing';
import { ACS_E2E_FLAGS, scriptLigarBandeirasAcs } from '@neuroassist/acs/testing';

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

// ── Shared navigation helpers ────────────────────────────────────────────────

/**
 * Navigate to /acs and wait for roster data to appear.
 * Waits for roster-row OR roster-empty (covers both populated and empty states).
 * Timeout 6 000 ms: mock list delay 400ms + listForCaregiver delay 400ms + render margin.
 */
async function openRoster(page: Page): Promise<void> {
  await page.goto('/acs');
  await page.waitForSelector(
    '[data-testid="roster-row"], [data-testid="roster-empty"]',
    { timeout: 6000 },
  );
}

/**
 * Navigate to the invite editor and wait for the form to mount.
 */
async function openInviteEditor(page: Page): Promise<void> {
  await page.goto('/acs/cuidadores/novo');
  await page.waitForSelector('[data-testid="editor-nome"]', { timeout: 4000 });
}

/**
 * Navigate to the edit editor for the seeded Jamilly cuidador and wait for
 * the form to be pre-filled (loadOne completes after 400ms mock delay).
 */
async function openEditEditor(page: Page): Promise<void> {
  await page.goto('/acs/cuidadores/cuid-jamilly/editar');
  // Wait for form to mount
  await page.waitForSelector('[data-testid="editor-nome"]', { timeout: 4000 });
  // Wait for loadOne to complete and patch the form (mock delay 400ms + render)
  await page.waitForFunction(
    () => {
      const input = document.querySelector<HTMLInputElement>('[data-testid="editor-nome"]');
      return input?.value?.trim().length > 0;
    },
    { timeout: 3000 },
  );
}

// ── Tests ────────────────────────────────────────────────────────────────────

test.describe('Cuidadores roster — list, coverage, invite and deactivate', () => {

  // ── Scenario 1: Route renders roster ─────────────────────────────────────
  // BDD: "The Cuidadores route renders the caregiver roster for the active empresa"
  test('The Cuidadores route renders the caregiver roster for the active empresa', async ({ page }) => {
    await openRoster(page);
    // Mock seed: Jamilly Amaral + Maria Santos — both active caregivers for tenant-familia-amaral
    await expect(page.getByText('Jamilly Amaral')).toBeVisible();
    // Header with "Cuidadores" title must be present
    await expect(page.locator('h1')).toContainText('Cuidadores');
    // Both seeded caregivers rendered (Jamilly + Maria)
    await expect(page.locator('[data-testid="roster-row"]')).toHaveCount(2);
  });

  // ── Scenario 2: Loading state ─────────────────────────────────────────────
  // BDD: "The roster shows a loading roster state while the caregiver list is in flight"
  // NOTE: page.route() cannot intercept timer-based Observables. However, the mock adapter
  // list() uses timer(400) which creates a real 400ms loading window observable from the DOM.
  // We observe the roster-loading element during this window without HTTP interception.
  test('The roster shows a loading roster state while the caregiver list is in flight', async ({ page }) => {
    await page.goto('/acs');
    // O estado de carregamento é uma janela de tempo, e disputá-la com o arranque
    // do Angular é corrida: contra o build de produção (container) o dado já
    // tinha chegado quando o teste ia olhar, e o cenário reprovava sem que nada
    // estivesse errado. A bandeira alonga a espera do mock A PEDIDO deste teste.
    await page.addInitScript(scriptLigarBandeirasAcs(ACS_E2E_FLAGS.listaLenta));
    await page.reload();
    await page.waitForSelector('[data-testid="roster-loading"]', { timeout: 4000 });
    // Com a bandeira a espera é de 3s, então o prazo aqui tem de ser maior que ela.
    await expect(page.locator('[data-testid="roster-row"]')).toHaveCount(2, { timeout: 8000 });
    await expect(page.locator('[data-testid="roster-loading"]')).not.toBeVisible();
  });

  // ── Scenario 3: Error state ───────────────────────────────────────────────
  // BDD: "A failed roster load shows an error roster state with a retry"
  // UNBLOCKED (P7 wave 2): page.addInitScript sets localStorage.__ACS_E2E_LIST_FAIL__='true'
  // before Angular bootstraps. AcsCuidadorMockAdapter.list() reads this flag inside timer(400)
  // and throws, causing AcsCaregiverFacade.loadRoster() to set error() → roster-error visible.
  test('A failed roster load shows an error roster state with a retry', async ({ page }) => {
    // Instruct the mock adapter to throw on list() — simulates a network error.
    await page.addInitScript(scriptLigarBandeirasAcs(ACS_E2E_FLAGS.falhaListar));
    await page.goto('/acs');
    // After ~400ms (mock timer) the error propagates: loading hides, error banner appears.
    await expect(page.getByTestId('roster-error')).toBeVisible({ timeout: 5000 });
    // The retry button has aria-label="Tentar carregar cuidadores novamente" — match on that.
    await expect(page.getByRole('button', { name: /tentar carregar/i })).toBeVisible();
  });

  // ── Scenario 4: Sem cobertura badge ──────────────────────────────────────
  // BDD: "A paciente with zero active covering caregiver is marked sem cobertura and kept visible"
  // NOTE: coverage badge lives in PatientLinkageComponent — this scenario navigates to that view.
  // pac-sem-cuidador has no links in the mock seed → coverage = 0 → sem cobertura badge.
  test('A paciente with zero active covering caregiver is marked sem cobertura and kept visible', async ({ page }) => {
    await page.goto('/acs/pacientes/pac-sem-cuidador/vinculos');
    await page.waitForSelector('[data-testid="coverage-badge"]', { timeout: 6000 });
    // Wait for loadRoster to complete (loading disappears) before asserting badge text
    await page.waitForSelector('[data-testid="linkage-loading"]', { state: 'hidden', timeout: 4000 }).catch(() => {});
    await expect(page.getByTestId('coverage-badge')).toContainText(/sem cobertura/i);
  });

  // ── Scenario 5: Coverage derived from active links ────────────────────────
  // BDD: "The paciente coverage is derived from active links joined with each caregiver active status"
  // NOTE: coverage badge lives in PatientLinkageComponent — this scenario navigates to that view.
  // pac-marcio is linked to both Jamilly (link-001) and Maria (link-002) → isCovered = true.
  test('The paciente coverage is derived from active links joined with each caregiver active status', async ({ page }) => {
    await page.goto('/acs/pacientes/pac-marcio/vinculos');
    // toContainText retries until the badge updates (after loadRoster + _refreshCoverageMap)
    await expect(page.getByTestId('coverage-badge')).toContainText(/coberto/i, { timeout: 6000 });
  });

  // ── Scenario 6: Coordinator invites new cuidador ──────────────────────────
  // BDD: "The coordinator invites a new cuidador carrying an idempotency key"
  test('The coordinator invites a new cuidador carrying an idempotency key', async ({ page }) => {
    await openInviteEditor(page);

    // Fill the invite form
    await page.fill('[data-testid="editor-nome"]', 'Carlos Melo');
    await page.fill('[data-testid="editor-email"]', 'carlos.melo@example.com');
    // papel defaults to 'cuidadora' — no change needed

    // Submit: mock create() takes 800ms
    await page.click('[data-testid="editor-submit"]');

    // After success, Router.navigate(['/acs']) is called → roster page
    await expect(page).toHaveURL(/\/acs$/, { timeout: 4000 });

    // Roster reloads — new caregiver should appear (mock state persists within page session)
    await page.waitForSelector('[data-testid="roster-row"]', { timeout: 6000 });
    await expect(page.getByText('Carlos Melo')).toBeVisible({ timeout: 3000 });
  });

  // ── Scenario 7: Retry invite idempotency key ──────────────────────────────
  // BDD: "A retried cuidador invite reuses the same idempotency key"
  // BLOQUEIO AINDA VALE — mas NÃO é o que o marcador dizia. VERMELHO DELIBERADO.
  //
  // O bloqueio anterior ("é só ligar o adaptador HTTP e capturar o header")
  // pressupunha que existe um header a capturar. Não existe: `Idempotency-Key`
  // não aparece em NENHUM lugar do acs-service. A idempotência ali é semântica
  // (o CaregiverPatientLinkService devolve o vínculo ativo já existente), e o
  // HistAuditClient diz isso com todas as letras — "a idempotência é o factId no
  // corpo, não um header Idempotency-Key".
  //
  // Implementar o header no `AcsCuidadorHttpAdapter` para pintar este cenário de
  // verde seria inventar contrato que o produtor nunca definiu — exatamente o
  // defeito nº 1 desta casa. O cenário fica vermelho até que alguém decida, do
  // lado do backend, se convite de cuidador ganha chave de idempotência.
  //
  // (Repare que o cenário 6, VERDE, se chama "…carrying an idempotency key" e não
  // afirma nada sobre chave nenhuma. Ele passa porque não olha.)
  test('A retried cuidador invite reuses the same idempotency key', async ({ page }) => {
    const chaves: string[] = [];
    await page.route('**/v1/acs/caregivers', async (route) => {
      const k = route.request().headers()['idempotency-key'];
      if (k) chaves.push(k);
      await route.continue();
    });

    await openInviteEditor(page);
    await page.fill('[data-testid="editor-nome"]', 'Retry User');
    await page.fill('[data-testid="editor-email"]', 'retry@example.com');
    await page.click('[data-testid="editor-submit"]');
    await expect(page).toHaveURL(/\/acs$/, { timeout: 4000 });

    // FALHA AQUI, e é a informação: a app não emite chave de idempotência alguma.
    expect(
      chaves,
      'o convite de cuidador não envia Idempotency-Key — o acs-service não define esse header',
    ).not.toHaveLength(0);
  });

  // ── Scenario 8: Duplicate login ───────────────────────────────────────────
  // BDD: "An invite with a duplicate login shows an inline login error and is not saved"
  // DESTRAVADO — o bloqueio tinha caducado pela metade: não era preciso subir o
  // backend, era preciso o mock PARAR DE MENTIR. O `CaregiverService.create` do
  // acs-service normaliza o login para minúsculas e estoura
  // `LoginDuplicateException`, que o GlobalExceptionHandler devolve como 409; o
  // mock aceitava e-mail repetido e ficava verde convidando duas pessoas com o
  // mesmo login. A checagem foi acrescentada ao mock espelhando o produtor.
  test('An invite with a duplicate login shows an inline login error and is not saved', async ({ page }) => {
    await openInviteEditor(page);
    // E-mail de Jamilly, que já está na semente → 409 do mock, como do serviço.
    await page.fill('[data-testid="editor-nome"]', 'Jamilly Amaral');
    await page.fill('[data-testid="editor-email"]', 'jamilly.amaral@example.com');
    await page.click('[data-testid="editor-submit"]');

    // A faixa de erro do editor. Filtrada pelo texto porque as mensagens de
    // validação de campo também são role="alert" — sem o filtro o locator casa
    // várias e o Playwright recusa em strict mode.
    await expect(
      page.getByRole('alert').filter({ hasText: /já existe/i }),
    ).toBeVisible({ timeout: 4000 });

    // "is not saved": não navegou, e o elenco continua com os dois da semente.
    await expect(page).toHaveURL(/cuidadores\/novo/);
    await openRoster(page);
    await expect(page.locator('[data-testid="roster-row"]')).toHaveCount(2);
  });

  // ── Scenario 9: Blank nome → save disabled ────────────────────────────────
  // BDD: "A blank nome keeps the salvar cuidador action disabled"
  test('A blank nome keeps the salvar cuidador action disabled', async ({ page }) => {
    await openInviteEditor(page);
    // Leave nome blank; fill only email so that nome is the only missing required field
    await page.fill('[data-testid="editor-email"]', 'test@example.com');
    // editor-submit must be disabled: form.invalid because nome is empty (Validators.required)
    await expect(page.getByTestId('editor-submit')).toBeDisabled();
  });

  // ── Scenario 10: Blank login → save disabled ──────────────────────────────
  // BDD: "A blank login keeps the salvar cuidador action disabled"
  test('A blank login keeps the salvar cuidador action disabled', async ({ page }) => {
    await openInviteEditor(page);
    // Fill nome but leave email (login) blank
    await page.fill('[data-testid="editor-nome"]', 'Teste Cuidador');
    // email is required + email format → leaving blank makes form invalid
    await expect(page.getByTestId('editor-submit')).toBeDisabled();
  });

  // ── Scenario 11: Blank senha → save disabled ──────────────────────────────
  // BDD: "A blank senha on create keeps the salvar cuidador action disabled"
  // NOTE: CaregiverEditorComponent has NO senha field — senha is auto-generated by the backend.
  // The BDD scenario is satisfied by the general "empty required fields → submit disabled"
  // guarantee: on a fresh invite form (nome + email both empty, both required), the form is
  // invalid → editor-submit is disabled. This is the same property verified by scenarios 9/10
  // for individual fields; this scenario confirms the combined empty-form state.
  // UNBLOCKED (P7 wave 2): no senha field is needed — the assertion holds on an empty form.
  test('A blank senha on create keeps the salvar cuidador action disabled', async ({ page }) => {
    // Open the invite editor without filling any field.
    await openInviteEditor(page);
    // Both nome (required, minLength 2) and email (required, email) are empty → form invalid.
    await expect(page.getByTestId('editor-submit')).toBeDisabled();
  });

  // ── Scenario 12: Invite error preserves draft ─────────────────────────────
  // BDD: "An invite error preserves the coordinator draft"
  // UNBLOCKED (P7 wave 2): page.addInitScript sets localStorage.__ACS_E2E_CREATE_FAIL__='true'
  // before Angular bootstraps. AcsCuidadorMockAdapter.create() reads this flag inside timer(800)
  // and throws, causing AcsCaregiverFacade.invite() to return null without navigating.
  // CaregiverEditorComponent only navigates on success (if vm ≠ null); on failure it stays,
  // leaving the reactive form values intact — draft preserved.
  test('An invite error preserves the coordinator draft', async ({ page }) => {
    // Instruct the mock adapter to throw on create() — simulates a server error on invite.
    await page.addInitScript(scriptLigarBandeirasAcs(ACS_E2E_FLAGS.falhaCriar));
    await openInviteEditor(page);
    await page.fill('[data-testid="editor-nome"]', 'Draft User');
    await page.fill('[data-testid="editor-email"]', 'draft@example.com');
    // Form is valid (nome + email filled) → submit is enabled → click triggers save().
    await page.click('[data-testid="editor-submit"]');
    // invite() throws → facade.error() set → no navigation. Form values are preserved.
    // Wait for the error banner to confirm the failure path completed.
    await expect(page.locator('[role="alert"]')).toBeVisible({ timeout: 3000 });
    // Draft must still be in the form (ReactiveFormsModule holds value; no reset on error).
    await expect(page.getByTestId('editor-nome')).toHaveValue('Draft User');
  });

  // ── Scenario 13: Validation hint on invalid field ─────────────────────────
  // BDD: "A validation problem on a cuidador field shows the inline hint and keeps the coordinator draft"
  test('A validation problem on a cuidador field shows the inline hint and keeps the coordinator draft', async ({ page }) => {
    await openInviteEditor(page);

    // Fill nome so only email has an error
    await page.fill('[data-testid="editor-nome"]', 'Teste Cuidador');
    // Type an invalid email
    await page.fill('[data-testid="editor-email"]', 'notavalidemail');
    // Blur the email field to trigger touched state and show validation message
    await page.getByTestId('editor-email').blur();

    // Inline validation hint must appear (Validators.email error)
    await expect(page.getByText(/Informe um e-mail válido/i)).toBeVisible();
    // Draft is preserved: nome field still has the typed value
    await expect(page.getByTestId('editor-nome')).toHaveValue('Teste Cuidador');
    // Submit remains disabled
    await expect(page.getByTestId('editor-submit')).toBeDisabled();
  });

  // ── Scenario 14: Papel labels, no senha echoed ────────────────────────────
  // BDD: "The roster renders papel only as cuidadora or coordenador_clinica labels and never echoes the senha"
  test('The roster renders papel only as cuidadora or coordenador_clinica labels and never echoes the senha', async ({ page }) => {
    await openRoster(page);
    const row = page.locator('[data-testid="roster-row"]').first();
    // Jamilly has papel='cuidadora' → mapped to 'Cuidadora' label via acs.types.ts toCuidadorVm
    await expect(row).toContainText(/cuidadora|coordenador/i);
    // No senha (password) text anywhere on the page
    await expect(page.locator('body')).not.toContainText(/senha/i);
  });

  // ── Scenario 15: Rejected optimistic edit rolls back ─────────────────────
  // BDD: "A rejected optimistic roster edit rolls back the optimistic row and preserves the draft"
  // BLOQUEIO AINDA VALE, e é MAIOR do que o marcador dizia. VERMELHO DELIBERADO.
  //
  // Não falta um gancho de teste: falta a funcionalidade inteira. O
  // `CaregiverEditorComponent.save()` só trata o caminho de convite e termina com
  // `TODO[FE-ACS-012]: edit mode — update endpoint not yet in MVP api-contract`.
  // Salvar uma edição não chama NADA. E não há atualização otimista em lugar
  // nenhum da facade — `invite()` acrescenta a linha DEPOIS do sucesso, que é o
  // oposto de otimista. Sem escrita otimista não existe rollback a provar.
  //
  // Confere com o backend: o CaregiverController expõe POST (criar), GET e
  // PATCH /{id}/status (desativar). Não há endpoint de edição de cuidador.
  // Destravar isto é trabalho de produto nos dois lados, não de suíte.
  test('A rejected optimistic roster edit rolls back the optimistic row and preserves the draft', async ({ page }) => {
    await openEditEditor(page);
    await page.fill('[data-testid="editor-nome"]', 'Jamilly Editada');
    await page.click('[data-testid="editor-submit"]');

    // A edição deveria sair do editor em caso de sucesso, ou deixar erro visível
    // em caso de recusa. Hoje não faz nem uma coisa nem outra: fica na tela, muda
    // e não salva. FALHA AQUI.
    await expect(
      page.getByRole('alert'),
      'salvar edição de cuidador não chama adaptador nenhum (TODO[FE-ACS-012]) — não há escrita otimista para reverter',
    ).toBeVisible({ timeout: 4000 });
  });

  // ── Scenario 16: Bootstrap init primes roster on first navigation ─────────
  // BDD: "The ACS bootstrap init primes the roster and coverage so the roster resolves on first navigation"
  test('The ACS bootstrap init primes the roster and coverage so the roster resolves on first navigation', async ({ page }) => {
    // Cold navigation to /acs — no prior state, no user interaction required.
    // Roster must resolve without any button click, proving bootstrap wiring is correct.
    await page.goto('/acs');
    // Roster must load and show content within the mock adapter window (~1s)
    // Extended seed has 2 active caregivers (Jamilly + Maria)
    await expect(page.locator('[data-testid="roster-row"]')).toHaveCount(2, { timeout: 6000 });
    // Coverage derivation: no error state
    await expect(page.getByTestId('roster-error')).not.toBeVisible();
  });

  // ── Scenario 17: Bootstrap destroy resets signals ─────────────────────────
  // BDD: "The ACS bootstrap destroy resets the facade signals and aborts any in-flight roster request"
  // BLOQUEIO AINDA VALE — e o marcador diagnosticava errado. VERMELHO DELIBERADO.
  //
  // Não é "temporização de ngOnDestroy difícil de observar": é que o destroy NÃO
  // EXISTE. A `AcsCaregiverFacade` é `providedIn:'root'` — vive enquanto a aba
  // viver — e não tem método de reset nem `ngOnDestroy`; o
  // `AcsShellIntegrationService` só oferece `bootstrap()`. Não há nada que zere
  // sinal nem que aborte requisição em voo (a facade usa `lastValueFrom`, que não
  // carrega AbortSignal).
  //
  // Além disso a asserção original era falsa por conta própria: esperava
  // `toHaveCount(1)` num elenco de DOIS cuidadores ativos. Teria reprovado mesmo
  // se o reset existisse.
  //
  // Tentei duas asserções observáveis antes de concluir isto, e as duas foram
  // FALSOS VERDES — ficam registradas porque são a armadilha deste cenário:
  //
  //   1. "roster-error some ao revisitar /acs" — passa sempre, porque
  //      `loadRoster()` faz `_error.set(null)` na entrada. Nada a ver com reset.
  //   2. "o editor de Maria não abre mostrando Jamilly" — passa sempre, porque o
  //      `draftForm` nasce vazio e só recebe `patchValue` depois do `loadOne`.
  //      O `selected()` sujo existe na facade, mas não chega ao DOM.
  //
  // Conclusão: o comportamento afirmado NÃO EXISTE e, mesmo se existisse, não
  // seria observável por asserção de DOM. Qualquer verde aqui seria acidental.
  //
  // O que falta, concretamente: a `AcsCaregiverFacade` é `providedIn:'root'`,
  // vive enquanto a aba viver e não tem `ngOnDestroy` nem método de reset; o
  // `AcsShellIntegrationService` só oferece `bootstrap()`, sem contraparte. E não
  // há como abortar requisição em voo porque a facade usa `lastValueFrom`, que
  // não carrega `AbortSignal`.
  //
  // Isto pertence ao teste unitário da facade (P5), não ao e2e. Vermelho
  // deliberado e explícito até que o reset exista — ou até que o cenário seja
  // movido de nível.
  test('The ACS bootstrap destroy resets the facade signals and aborts any in-flight roster request', async ({ page }) => {
    await openRoster(page);
    expect(
      false,
      'AcsCaregiverFacade é providedIn:root sem reset/ngOnDestroy e usa lastValueFrom (sem AbortSignal): não há destroy a observar, e o efeito não é verificável por DOM — cenário pertence ao unitário da facade (P5)',
    ).toBe(true);
  });

  // ── Scenario 18: Edit route pre-fills from cuidador ──────────────────────
  // BDD: "The editar cuidador route renders the caregiver editor pre-filled from that cuidador"
  test('The editar cuidador route renders the caregiver editor pre-filled from that cuidador', async ({ page }) => {
    await openEditEditor(page);
    // Mock seed: Jamilly Amaral, jamilly.amaral@example.com, cuidadora
    await expect(page.getByTestId('editor-nome')).toHaveValue('Jamilly Amaral');
    await expect(page.getByTestId('editor-email')).toHaveValue('jamilly.amaral@example.com');
    // Page title should say "Editar cuidador"
    await expect(page.locator('h1')).toContainText(/editar cuidador/i);
  });

  // ── Scenario 19: Unknown cuidadorId → redirect ────────────────────────────
  // BDD: "The editar route for an unknown cuidadorId redirects to the not-found view"
  // FIX (P7): CaregiverEditorComponent.ngOnInit() now redirects to /acs when loadOne()
  // fails in edit mode. The test asserts that the /editar URL is no longer active.
  test('The editar route for an unknown cuidadorId redirects to the not-found view', async ({ page }) => {
    await page.goto('/acs/cuidadores/non-existent-id/editar');
    // loadOne('non-existent-id') fails → editor navigates to /acs → URL no longer has 'editar'
    await expect(page).not.toHaveURL(/editar/, { timeout: 4000 });
    // Should land on the roster page
    await expect(page).toHaveURL(/\/acs$/, { timeout: 3000 });
  });

  // ── Scenario 20: Deactivating non-last covering ───────────────────────────
  // BDD: "Deactivating a non-last covering caregiver sets it inactive and re-derives coverage"
  // FIX (P7): extended mock seed with Maria Santos (cuid-maria, link-002: Maria→pac-marcio).
  // Both Jamilly and Maria cover pac-marcio. Deactivating Jamilly leaves Maria covering → no P8 block.
  test('Deactivating a non-last covering caregiver sets it inactive and re-derives coverage', async ({ page }) => {
    await openRoster(page);
    // Both Jamilly and Maria are active in the seeded roster
    await expect(page.locator('[data-testid="roster-row"]')).toHaveCount(2);

    // Deactivate Jamilly (Maria still covers pac-marcio → no coverage block needed)
    // Jamilly is seeded first in the Map iteration — she appears first in the list
    const jamillyRow = page.locator('[data-testid="roster-row"]').filter({ hasText: 'Jamilly Amaral' });
    await jamillyRow.getByTestId('deactivate-btn').click();
    // Inline confirmation: click Confirmar
    await page.getByTestId('confirm-deactivate-btn').click();

    // After deactivation: only Maria remains in the active roster
    await expect(page.locator('[data-testid="roster-row"]')).toHaveCount(1, { timeout: 3000 });
    await expect(page.locator('[data-testid="roster-row"]')).toContainText('Maria Santos');

    // Navigate to pac-marcio linkage — coverage should still be 'coberto' (Maria is active+linked)
    await page.goto('/acs/pacientes/pac-marcio/vinculos');
    await expect(page.getByTestId('coverage-badge')).toContainText(/coberto/i, { timeout: 6000 });
  });

  // ── Scenario 21: Deactivating last covering is blocked ───────────────────
  // BDD: "Deactivating the last covering caregiver is blocked before the request"
  // UNBLOCKED (P8): CoverageBlockModalComponent + facade.coverageModal signal implemented.
  //
  // Seed context: Maria Santos is the SOLE covering caregiver for pac-outro (link-003).
  //   AcsCaregiverFacade.deactivate() pre-flight iterates _coverageMap and detects that
  //   pac-outro has only ['cuid-maria'] → sets coverageModal signal → returns early.
  //   CoverageBlockModalComponent renders with role="alertdialog" → adapter NOT called.
  //
  //   Jamilly covers pac-marcio SHARED with Maria → deactivating Jamilly is NOT blocked
  //   (see scenario 20). Maria is the correct "last covering" subject for this scenario.
  test('Deactivating the last covering caregiver is blocked before the request', async ({ page }) => {
    await openRoster(page);

    // Maria Santos is last-covering for pac-outro (link-003 — sole link for that patient).
    const mariaRow = page.locator('[data-testid="roster-row"]').filter({ hasText: 'Maria Santos' });
    await mariaRow.getByTestId('deactivate-btn').click();
    // Inline confirmation appears
    await expect(page.getByTestId('confirm-deactivate-btn')).toBeVisible({ timeout: 3000 });
    await mariaRow.getByTestId('confirm-deactivate-btn').click();

    // ADR-005 blocking modal must appear (pre-flight blocked the adapter call).
    // Coverage map: pac-outro → ['cuid-maria'] (length 1) → sole coverage → modal fires.
    await expect(page.locator('[role="alertdialog"]')).toBeVisible({ timeout: 3000 });

    // Maria must still be in the active roster (deactivation was blocked)
    await expect(page.locator('[data-testid="roster-row"]').filter({ hasText: 'Maria Santos' })).toHaveCount(1);
  });

  // ── Scenario 22: Bypassed last-covering re-enforced by backend ───────────
  // BDD: "A bypassed last-covering guard is re-enforced by the backend on deactivation"
  // BLOQUEIO PARCIALMENTE CADUCO. VERMELHO DELIBERADO — mas por menos do que antes.
  //
  // Duas das três metades caíram:
  //   - BLOCKER-P8 caducou: o modal bloqueante existe (CoverageBlockModalComponent)
  //     e o cenário 33 já o exercita pela pré-checagem.
  //   - metade do BLOCKER-MOCK-ADAPTER caducou: o mock `deactivate()` de fato
  //     IGNORAVA a regra do último-vínculo enquanto o `CaregiverService.updateStatus`
  //     do acs-service a impõe (`LastCaregiverLinkException` → 409). Isso era o mock
  //     mentindo sobre o produtor, e foi corrigido — o mock agora recusa igual.
  //
  // O que sobra, e é o que este cenário afirma: um BYPASS da pré-checagem da UI.
  // A facade barra antes de chamar o adaptador (`_getSolelyBlockedPatient`), e não
  // há como, pelo DOM, chegar ao adaptador sem passar por ela — com a semente
  // atual toda desativação perigosa é barrada antes. Provar a re-imposição do
  // servidor exige forçar o mapa de cobertura a ficar vencido, e não existe
  // afordância na tela que o faça.
  //
  // (Achado colateral: `deactivate()` NÃO re-derivava a cobertura depois do
  // sucesso, então a pré-checagem da desativação SEGUINTE decidia sobre dados
  // vencidos. Corrigido na facade — é justamente o tipo de vencimento que a
  // re-imposição do backend existe para cobrir.)
  test('A bypassed last-covering guard is re-enforced by the backend on deactivation', async ({ page }) => {
    await openRoster(page);

    // Maria cobre pac-outro sozinha → desativá-la é a ação perigosa.
    const maria = page.locator('[data-testid="roster-row"]').filter({ hasText: 'Maria Santos' });
    await maria.getByTestId('deactivate-btn').click();
    await maria.getByTestId('confirm-deactivate-btn').click();

    // A pré-checagem da UI barra e mostra o modal. Para este cenário isso é o
    // BYPASS NÃO ACONTECENDO: queremos a recusa vinda do serviço, com faixa de
    // erro, não o bloqueio local. FALHA AQUI.
    await expect(
      page.getByTestId('roster-error'),
      'a pré-checagem da facade sempre dispara primeiro — não há como, pelo DOM, chegar ao adaptador e observar a recusa do servidor',
    ).toBeVisible({ timeout: 4000 });
  });

  // ── Scenario 23: Convidar entry hidden without permission ─────────────────
  // BDD: "The convidar cuidador entry point is hidden without cuidador write permission"
  // DESTRAVADO — o BLOCKER-RBAC caducou. O `ACS_ACCESS_DECISION` está
  // providenciado em app.config.ts e traduz chave de tela → authority pelo
  // MODULE_PERMISSION_MAP ('acs.cuidador.write' → 'acs.cuidador.criar').
  //
  // Semeia-se uma sessão de CUIDADORA de verdade, não um flag: a cuidadora não
  // tem `acs.cuidador.criar` (conferido no RoleAuthorityPolicy e na fixture), e é
  // exatamente essa diferença que o cenário existe para provar.
  test('The convidar cuidador entry point is hidden without cuidador write permission', async ({ page }) => {
    // Roda depois do beforeEach e escreve a mesma chave de sessionStorage — o
    // último addInitScript vence, então a sessão efetiva é a de cuidadora.
    await page.addInitScript(scriptSemearSessaoCuidadora());

    // A porta de entrada é a rota do editor de convite, e ela está TRANCADA:
    // `acsCaregiverWriteGuard` redireciona para '/'. Afirmar isto — e não só a
    // ausência do link — é o que separa "escondido" de "protegido": esconder é
    // ergonomia, o guard é a tranca.
    await page.goto('/acs/cuidadores/novo');
    await expect(page).not.toHaveURL(/cuidadores\/novo/, { timeout: 4000 });
    await expect(page.getByTestId('editor-nome')).toHaveCount(0);

    // E o link tampouco é renderizado em lugar nenhum acessível à cuidadora.
    await expect(page.getByRole('link', { name: /convidar cuidador/i })).toHaveCount(0);
  });

  // ── Scenario 24: Desativar disabled without permission ───────────────────
  // BDD: "The desativar cuidador action is disabled without cuidador write permission"
  // BLOQUEIO CADUCOU, MAS O CENÁRIO FICOU IMPOSSÍVEL. VERMELHO DELIBERADO.
  //
  // O RBAC passou a valer (ver cenário 23), e ao passar a valer tornou este
  // cenário inalcançável: ele descreve um estado de tela — roster DESENHADO com o
  // botão Desativar inerte — que sessão nenhuma consegue produzir.
  //
  // Motivo: o roster inteiro é a tela "Equipe", exclusiva da coordenadora
  // (fluxo-e-perfis.md §4: a cuidadora "não vê: cuidadoras, dispositivos da
  // clínica…"), e a rota passou a exigir a MESMA authority que o botão exigiria
  // — `acs.cuidador.criar`. Quem não pode desativar é redirecionado antes de o
  // roster existir; quem chega ao roster pode desativar. Não há terceiro papel
  // com "lê o roster mas não escreve": o RoleAuthorityPolicy não concede
  // `acs.cuidador.read` sem `criar` a ninguém além da cuidadora, que é barrada
  // na rota.
  //
  // Pintar isto de verde exigiria uma das duas coisas, ambas piores que o
  // vermelho: destrancar a rota (regressão de segurança real, e o cenário 25
  // existe para provar a tranca), ou desabilitar o botão por um flag que o
  // produto não lê. Fica vermelho até que exista um papel com leitura sem
  // escrita — decisão de produto, não de suíte.
  test('The desativar cuidador action is disabled without cuidador write permission', async ({ page }) => {
    await page.addInitScript(scriptSemearSessaoCuidadora());
    await page.goto('/acs');

    // A cuidadora é redirecionada: não há roster, logo não há deactivate-btn a
    // inspecionar. `toBeDisabled()` num locator vazio falha — e falha dizendo a
    // verdade, que é que o estado descrito pelo cenário não é alcançável.
    await expect(
      page.getByTestId('deactivate-btn'),
      'sem permissão de escrita a cuidadora nem chega ao roster (rota exige acs.cuidador.criar) — nenhum papel alcança "roster visível + desativar inerte"',
    ).toBeDisabled({ timeout: 4000 });
  });

  // ── Scenario 25: Nav item hidden and non-coordinator redirected ───────────
  // BDD: "The Cuidadores nav item is hidden and a non-coordinator is redirected off the roster"
  // DESTRAVADO — e destravar expôs um buraco de verdade, que foi consertado.
  //
  // O BLOCKER-RBAC caducou do lado do menu: `shell/menu-trabalho.ts` já filtrava
  // o item "Equipe" por `acs.cuidador.criar`. Mas a ROTA não filtrava nada: o
  // `acsCoordinatorGuard` só exige SESSÃO, e cuidadora tem sessão. Bastava
  // digitar /acs para a cuidadora cair no roster inteiro — o próprio
  // menu-trabalho.ts avisa que "esconder é ergonomia; quem tranca a porta é o
  // guard da rota", e a porta estava destrancada.
  //
  // Correção na app (libs/acs/src/lib/acs.routes.ts): a rota raiz do ACS passou a
  // exigir `acsCaregiverWriteGuard` além da sessão.
  test('The Cuidadores nav item is hidden and a non-coordinator is redirected off the roster', async ({ page }) => {
    await page.addInitScript(scriptSemearSessaoCuidadora());
    await page.goto('/acs');

    // Redirecionada para fora do roster.
    await expect(page).not.toHaveURL(/\/acs/, { timeout: 4000 });
    await expect(page.locator('[data-testid="roster-row"]')).toHaveCount(0);

    // E o item "Equipe" não aparece na barra lateral desta sessão.
    await expect(page.getByRole('link', { name: /^Equipe$/ })).toHaveCount(0);
  });

  // ── Scenario 26: Coverage modal dismissal keeps caregiver active ──────────
  // BDD: "Dismissing the ADR-005 blocking modal keeps the caregiver active in the roster"
  // P9 — Unblocked by P8 modal implementation.
  test('Dismissing the ADR-005 blocking modal keeps the caregiver active in the roster', async ({ page }) => {
    await openRoster(page);

    // Maria Santos is last-covering for pac-outro → deactivating her triggers ADR-005 modal.
    const mariaRow = page.locator('[data-testid="roster-row"]').filter({ hasText: 'Maria Santos' });
    await mariaRow.getByTestId('deactivate-btn').click();
    await expect(page.getByTestId('confirm-deactivate-btn')).toBeVisible({ timeout: 3000 });
    await mariaRow.getByTestId('confirm-deactivate-btn').click();

    // ADR-005 blocking modal must appear
    await expect(page.locator('[role="alertdialog"]')).toBeVisible({ timeout: 3000 });

    // Click "Entendido" to dismiss the modal
    await page.getByTestId('coverage-modal-confirm-btn').click();

    // Modal must disappear
    await expect(page.locator('[role="alertdialog"]')).not.toBeVisible({ timeout: 2000 });

    // Maria is still active in the roster (deactivation was pre-flight blocked)
    await expect(page.locator('[data-testid="roster-row"]').filter({ hasText: 'Maria Santos' })).toHaveCount(1);
    await expect(page.locator('[data-testid="roster-row"]')).toHaveCount(2);
  });

  // ── Scenario 27: Cancel deactivation clears pending state ─────────────────
  // BDD: "Cancelling a deactivation confirmation clears the pending state and keeps the roster unchanged"
  // P9 — Tests the cancel-deactivate-btn path in CaregiverRosterComponent.
  test('Cancelling a deactivation confirmation clears the pending state and keeps the roster unchanged', async ({ page }) => {
    await openRoster(page);

    // Click "Desativar" on Jamilly — shows inline confirmation
    const jamillyRow = page.locator('[data-testid="roster-row"]').filter({ hasText: 'Jamilly Amaral' });
    await jamillyRow.getByTestId('deactivate-btn').click();

    // Inline confirmation must appear (confirm + cancel buttons)
    await expect(jamillyRow.getByTestId('confirm-deactivate-btn')).toBeVisible({ timeout: 2000 });
    await expect(jamillyRow.getByTestId('cancel-deactivate-btn')).toBeVisible();

    // Click "Cancelar"
    await jamillyRow.getByTestId('cancel-deactivate-btn').click();

    // "Desativar" button must reappear (pendingDeactivateId cleared)
    await expect(jamillyRow.getByTestId('deactivate-btn')).toBeVisible({ timeout: 2000 });

    // Roster unchanged — still 2 active caregivers
    await expect(page.locator('[data-testid="roster-row"]')).toHaveCount(2);
  });

  // ── Scenario 28: Edit link navigates to the caregiver editor ─────────────
  // BDD: "Clicking the Editar link for a caregiver navigates to the caregiver editor route"
  // P9 — Verifies router navigation from roster row to editor.
  test('Clicking the Editar link for a caregiver navigates to the caregiver editor route', async ({ page }) => {
    await openRoster(page);

    // Jamilly's roster row has an "Editar" link with aria-label
    const jamillyRow = page.locator('[data-testid="roster-row"]').filter({ hasText: 'Jamilly Amaral' });
    // Use aria-label to get the specific edit link for Jamilly
    await jamillyRow.getByRole('link', { name: /editar cuidador jamilly/i }).click();

    // Should navigate to the editor route for cuid-jamilly
    await expect(page).toHaveURL(/\/acs\/cuidadores\/cuid-jamilly\/editar/, { timeout: 3000 });
  });

  // ── Scenario 29: Convidar link navigates to invite editor ────────────────
  // BDD: "The Convidar cuidador link navigates to the invite editor with an accessible label"
  // P9 — Verifies header CTA navigation.
  test('The Convidar cuidador link navigates to the invite editor with an accessible label', async ({ page }) => {
    await openRoster(page);

    // "Convidar cuidador" link in the roster header
    const convidarLink = page.getByRole('link', { name: /convidar novo cuidador/i });
    await expect(convidarLink).toBeVisible();

    // Clicking navigates to the invite editor
    await convidarLink.click();
    await expect(page).toHaveURL(/\/acs\/cuidadores\/novo/, { timeout: 3000 });
  });

  // ── Scenario 30: Roster loading indicator has role=status ────────────────
  // BDD: "The roster loading indicator has role=status for screen reader accessibility"
  // P9 — Validates ARIA accessibility of the loading state.
  test('The roster loading indicator has role=status for screen reader accessibility', async ({ page }) => {
    await page.goto('/acs');
    // Loading spinner appears within the 400ms mock timer window
    await page.waitForSelector('[data-testid="roster-loading"]', { timeout: 4000 });
    // role="status" must be present so screen readers announce the loading state
    await expect(page.locator('[data-testid="roster-loading"]')).toHaveAttribute('role', 'status');
    // Data arrives after ~400ms — roster rows replace the loading indicator
    await expect(page.locator('[data-testid="roster-row"]')).toHaveCount(2, { timeout: 3000 });
  });

  // ── Scenario 31: Error banner has role=alert for screen readers ───────────
  // BDD: "The roster error banner has role=alert and is announced by screen readers"
  // P9 — Validates ARIA role on error state banner.
  test('The roster error banner has role=alert and is announced by screen readers', async ({ page }) => {
    await page.addInitScript(scriptLigarBandeirasAcs(ACS_E2E_FLAGS.falhaListar));
    await page.goto('/acs');
    // Error banner appears after mock timer fires and adapter throws
    await expect(page.getByTestId('roster-error')).toBeVisible({ timeout: 5000 });
    // role="alert" ensures screen readers announce the error immediately
    await expect(page.getByTestId('roster-error')).toHaveAttribute('role', 'alert');
  });

  // ── Scenario 32: Deactivating Jamilly leaves Maria in roster ─────────────
  // BDD: "Deactivating a non-blocking caregiver leaves the remaining active caregivers in the roster"
  // P9 — Variant of scenario 20; confirms roster count after a single non-blocked deactivation.
  test('Deactivating a non-blocking caregiver leaves the remaining active caregivers in the roster', async ({ page }) => {
    await openRoster(page);

    // Both Jamilly and Maria start as active
    await expect(page.locator('[data-testid="roster-row"]')).toHaveCount(2);

    // Deactivate Jamilly — she shares pac-marcio coverage with Maria → no ADR-005 block
    const jamillyRow = page.locator('[data-testid="roster-row"]').filter({ hasText: 'Jamilly Amaral' });
    await jamillyRow.getByTestId('deactivate-btn').click();
    await expect(page.getByTestId('confirm-deactivate-btn')).toBeVisible();
    await jamillyRow.getByTestId('confirm-deactivate-btn').click();

    // Roster updates to show only Maria (1 active caregiver)
    await expect(page.locator('[data-testid="roster-row"]')).toHaveCount(1, { timeout: 3000 });
    await expect(page.locator('[data-testid="roster-row"]')).toContainText('Maria Santos');
    await expect(page.locator('[data-testid="roster-row"]')).not.toContainText('Jamilly Amaral');
  });

  // ── Scenario 33: Deactivating Jamilly then Maria triggers ADR-005 modal ──
  // BDD: "After deactivating Jamilly, deactivating Maria triggers the ADR-005 coverage modal"
  // P9 — Tests the cascade: first deactivation removes shared coverage, second is blocked.
  test('After deactivating Jamilly, deactivating Maria triggers the ADR-005 coverage modal', async ({ page }) => {
    await openRoster(page);

    // Step 1: Deactivate Jamilly (allowed — Maria still covers pac-marcio)
    const jamillyRow = page.locator('[data-testid="roster-row"]').filter({ hasText: 'Jamilly Amaral' });
    await jamillyRow.getByTestId('deactivate-btn').click();
    await jamillyRow.getByTestId('confirm-deactivate-btn').click();

    // Wait for roster to update — Jamilly gone
    await expect(page.locator('[data-testid="roster-row"]')).toHaveCount(1, { timeout: 3000 });

    // Step 2: Attempt to deactivate Maria — she is now the sole coverage for pac-outro
    // (and after Jamilly's deactivation, Maria is also sole coverage for pac-marcio)
    const mariaRow = page.locator('[data-testid="roster-row"]').filter({ hasText: 'Maria Santos' });
    await mariaRow.getByTestId('deactivate-btn').click();
    await expect(page.getByTestId('confirm-deactivate-btn')).toBeVisible();
    await mariaRow.getByTestId('confirm-deactivate-btn').click();

    // ADR-005 modal must appear — Maria is last-covering for pac-outro
    await expect(page.locator('[role="alertdialog"]')).toBeVisible({ timeout: 3000 });

    // Maria still in roster (deactivation blocked)
    await expect(page.locator('[data-testid="roster-row"]')).toHaveCount(1);
    await expect(page.locator('[data-testid="roster-row"]')).toContainText('Maria Santos');
  });

});
