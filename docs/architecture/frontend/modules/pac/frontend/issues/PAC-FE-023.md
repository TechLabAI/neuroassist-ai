## [PAC-FE-023] Playwright e2e — command-catalog.feature (12 cenários)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`apps/pac-e2e/src/command-catalog.spec.ts` implementa os 12 cenários Gherkin de `docs/architecture/frontend/modules/pac/frontend/e2e/command-catalog.feature` como testes Playwright, cobrindo: route rendering, load states (loading/error/empty), KPI honest sync, RBAC de UI (write/read permissions) e redirect de guard.

**Context / Problem statement:**
- 12 cenários incluem: renderização da rota, loading spinner, error card + retry, empty state, KPI pendentesSync=2, saved-but-not-acknowledged → 'pendente', device-acked → 'ativo', write-permission hidden, read-permission hidden nav, registered paciente nav visible, no-paciente redirect.
- 6 cenários EARS (mapeados a SHALL clauses) + 6 orphans adicionais (mais granulares que as clauses EARS).
- Feature usa mock de `pac-service` (Page Object com intercept Playwright para simular respostas BE).

**Approach (execution path):**
1. Criar `apps/pac-e2e/src/command-catalog.spec.ts` usando `@playwright/test`.
2. Implementar `PacCatalogPage` page object: `goto()`, `waitForCatalog()`, `getKpiPendenteCount()`, `getRowSyncState(index)`, `getNavItem()`, `getNovoComandoButton()`.
3. Usar `page.route('**/api/pac/v1/pacientes/*/comandos*', ...)` para mock de respostas.
4. Implementar cada `test(scenarioTitle, ...)` conforme os 12 cenários do `.feature`.
5. Cenário "Device-acknowledged → ativo no dispositivo": configurar mock que retorna device-ack no INT-FE-PAC-001 stub.

**Non-goals:**
- Sem testes e2e de backend real — tudo mocado via Playwright `page.route`.
- Sem setup de banco de dados para os e2e.

**Layer(s):**
frontend / apps/pac-e2e

**Bounded context:**
pac

**Service execution spec(s):**
- `docs/architecture/frontend/modules/pac/frontend/e2e/command-catalog.feature`

**Architecture alignment:**
- Playwright Test runner
- Tag Nx: `scope:pac`, `type:e2e`

**Implementation checklist (by file):**

**App: apps/pac-e2e**
- [ ] `apps/pac-e2e/src/command-catalog.spec.ts`
- [ ] `apps/pac-e2e/src/page-objects/pac-catalog.page.ts`

**Tests**
- [ ] `npx nx e2e pac-e2e --testFile=command-catalog.spec.ts` PASS (12/12 scenarios)

**Milestone:**
Wave 9 / P9 e2e + Hardening

**Labels:**
type:e2e
layer:frontend
scope:pac
priority:P9

**Acceptance criteria:**
- `npx nx e2e pac-e2e --testFile=command-catalog.spec.ts` PASS: 12/12 cenários GREEN.
- Cenário "Saved comando edit not acknowledged stays pendente de sincronização" → PASS.
- Cenário "Caller lacking read permission → Calibração nav item hidden" → PASS.

**Dependencies:**
- PAC-FE-014 (CommandCatalogComponent implementado)
- PAC-FE-017 (guard redirect implementado)
- PAC-FE-019 (SyncStatusBadgeComponent)

**Data impact:**
Nenhum (mocks).

**API / Contract impact:**
- Moca `GET /api/pac/v1/pacientes/{pid}/comandos`.

**Testing / Evidence required:**
- CI report com 12/12 green; screenshot de cada load state.

**Risks / Notes:**
- O cenário de device-ack ("ativo no dispositivo") só é testável após INT-FE-PAC-001 implementar o adapter real; enquanto isso, o e2e usa mock que simula a transição de syncState.
