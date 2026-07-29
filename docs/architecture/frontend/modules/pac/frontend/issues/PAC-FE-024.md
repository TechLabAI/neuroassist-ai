## [PAC-FE-024] Playwright e2e — command-editor.feature (42 cenários + emergency gate)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`apps/pac-e2e/src/command-editor.spec.ts` implementa os ~42 cenários Gherkin de `command-editor.feature`, cobrindo routes, validações síncronas, lifecycle gating, save/idempotência/concorrência, emergency gate (fail-closed + advisory), recalibrate/retire, todos os 12 ProblemDetail mappings e composição GEST/feature flags.

**Context / Problem statement:**
- É a suíte e2e mais extensa do PAC: cobre rotas do editor (4), validações sync (4), lifecycle buttons (5), save+idempotência (6), validate/publish/gate (5), recalibrate/retire (2), ProblemDetails (9), GEST composition (2), feature flags (2), shell integration (3).
- Cenário safety-critical: "Emergency-not-detectable → blocking modal + comando stays validated" — deve verificar que o modal NÃO pode ser dispensado sem ação do usuário.
- Cenários de orphans incluem: "Inline novo tipo flag off hides affordance", "Deferred edge sync flag off collapses badge", "Shell loading PAC descriptor contributes nav without eager load".
- 39 orphans totais do feature PAC são distribuídos entre esta suíte (maioria) e PAC-FE-023/025.

**Approach (execution path):**
1. Criar `apps/pac-e2e/src/command-editor.spec.ts`.
2. Implementar `PacEditorPage` page object: `gotoNew()`, `gotoEdit(id)`, `fillRotulo(text)`, `setTiming(min, max)`, `clickSalvar()`, `clickValidar()`, `clickPublicar()`, `clickRecalibrar()`, `clickRetirar()`, `getLifecycleButtonState(action)`, `getSyncBannerText()`, `getBlockingModal()`.
3. Usar `page.route` para mock de cada ProblemDetail response (12 tipos).
4. Testar emergency gate: mock `422 emergency-not-detectable` → assert blocking modal presente e não-dismissível.
5. Testar idempotência: salvar 2x com mesmo draft → verificar que apenas 1 POST ocorreu (via `page.route` request log).

**Non-goals:**
- Sem testes de câmera/GEST real — o slot de live-test é mocado.
- Sem testes de performance (performance budget é separado).

**Layer(s):**
frontend / apps/pac-e2e

**Bounded context:**
pac

**Service execution spec(s):**
- `docs/architecture/frontend/modules/pac/frontend/e2e/command-editor.feature`

**Architecture alignment:**
- Playwright Test runner
- Tag Nx: `scope:pac`, `type:e2e`

**Implementation checklist (by file):**

**App: apps/pac-e2e**
- [ ] `apps/pac-e2e/src/command-editor.spec.ts`
- [ ] `apps/pac-e2e/src/page-objects/pac-editor.page.ts`
- [ ] `apps/pac-e2e/src/fixtures/problem-detail-mocks.ts`

**Tests**
- [ ] `npx nx e2e pac-e2e --testFile=command-editor.spec.ts` PASS (42/42 scenarios)

**Milestone:**
Wave 9 / P9 e2e + Hardening

**Labels:**
type:e2e
layer:frontend
scope:pac
priority:P9

**Acceptance criteria:**
- `npx nx e2e pac-e2e --testFile=command-editor.spec.ts` PASS: 42/42 cenários GREEN.
- Cenário "Emergency-not-detectable → blocking modal" → modal presente, dismiss impossible.
- Cenário "Retired editor comando → every lifecycle action disabled" → PASS.
- Cenário "Retried save reuses same idempotency key" → POST count === 1 no request log.
- Cenário "Composing GEST live-test at shell page does not import gest scope into PAC library" → nenhum import de `scope:gest` detectado em runtime (usar lint, não e2e; mas o e2e verifica que o slot funciona).

**Dependencies:**
- PAC-FE-015 (CommandEditorComponent implementado)
- PAC-FE-021 (error handler — todos os 12 error types)
- PAC-FE-011 (PacComandoFacade — lifecycle signals)

**Data impact:**
Nenhum (mocks).

**API / Contract impact:**
- Moca todos os 10 endpoints do PacComandoClient + 12 respostas de erro.

**Testing / Evidence required:**
- CI report com 42/42 green; screenshot do blocking modal safety-critical.

**Risks / Notes:**
- Suíte grande (~42 testes): dividir em `describe` blocks por grupo temático (routes, validation, lifecycle, errors) para facilitar debugging de falhas.
- O cenário "PAC bootstrap destroy resets facade signals" pode ser flaky se o guard de re-entrada não abortar o GET em-flight; monitorar.
