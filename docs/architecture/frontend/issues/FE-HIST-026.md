## [FE-HIST-026] hist e2e: Playwright — history-timeline.feature

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
Testes Playwright implementados em `apps/hist-e2e` cobrindo os cenários do `history-timeline.feature`: navegação, filtros de período, estados de loading/empty/error, loadmore e chain-verification badge.

**Context / Problem statement:**
- E2e feature: `docs/architecture/frontend/modules/hist/e2e/history-timeline.feature`.
- generator-config.yml `e2e.features_path` aponta para este arquivo.
- Cenários: default period, period change, empty state, load error + retry, loadmore, chain-verification (coordinator).
- FE-GAP-09..12 incluem cenários de erro que precisam de cobertura e2e (401, 403, 503).

**Approach (execution path):**
1. Mapear cada Gherkin scenario para um `test()` Playwright em `apps/hist-e2e/src/e2e/history-timeline.spec.ts`.
2. Usar `page.route()` para mock das respostas HTTP (sem depender de backend real).
3. Cenários obrigatórios:
   - Navegação `/historico` sem period param → default `hoje`.
   - Troca de período → URL atualiza, skeleton, nova lista.
   - Empty state: GET /history retorna `{ items: [], total: 0 }`.
   - Error state: GET /history retorna 503 → inline card → retry.
   - 401 → redirect `/login`.
   - 403 → access message.
   - LoadMore → append; foco no primeiro novo item.
   - Chain verification badge (mock `hist:read:all` scope).
4. `nx e2e hist-e2e` PASS.

**Non-goals:**
- Não duplicar unit tests de componente.
- Não testar backend real.

**Layer(s):**
frontend / e2e

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/e2e/history-timeline.feature`
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-06-error-states.md`

**Architecture alignment:**
- Playwright e2e · `apps/hist-e2e/`

**Implementation checklist (by file):**
- 🔲 `apps/hist-e2e/src/e2e/history-timeline.spec.ts`
- 🔲 `apps/hist-e2e/src/support/hist-mocks.ts` (page.route mocks)
- 🔲 `nx e2e hist-e2e --testFile=history-timeline.spec.ts` PASS

**Milestone:** Wave P9 / e2e + Hardening

**Labels:**
type:test · layer:frontend · scope:hist · priority:P9

**Acceptance criteria:**
- Todos os cenários de `history-timeline.feature` têm cobertura Playwright.
- Cenários de erro (401/403/503) cobertos com mocks.
- `npx nx e2e hist-e2e` PASS.

**Dependencies:**
- FE-HIST-009, FE-HIST-018, FE-HIST-020

**Risks / Notes:**
- Se `apps/hist-e2e` não existe, registrar via `fe-nx-register-module.py` (D-487) antes.
