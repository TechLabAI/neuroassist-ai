## [FE-HIST-025] hist performance: chunk budget e skeleton timing

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
Gate de performance automatizado no build: chunk `@neuroassist/hist` ≤ 120 KB gzip; skeleton renderizado dentro de 100 ms da navegação (medido via Playwright Navigation Timing).

**Context / Problem statement:**
- SPEC-08 §1: lazy chunk ≤ 120 KB gzip; skeleton dentro de 100 ms.
- SPEC-08 §5: "o ecrã nunca fica frozen" — skeleton é a first-class fast path.
- Angular budget file já deve ter entry para o lazy chunk.

**Approach (execution path):**
1. Adicionar `budgets` entry em `angular.json` para o chunk `@neuroassist/hist`: `maximumWarning: "100kb"`, `maximumError: "120kb"` (gzip).
2. Playwright timing: usar `page.evaluate(() => performance.getEntriesByType('navigation'))` para medir TTI até skeleton.
3. Smoke test de performance: `expect(skeletonTiming).toBeLessThan(100)`.

**Non-goals:**
- Não substituir testes funcionais por testes de performance.

**Layer(s):**
frontend / feature-timeline

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-08-performance.md` (§1, §5)

**Architecture alignment:**
- Tag Nx: `scope:hist`

**Implementation checklist (by file):**
- 🔲 `angular.json` — budgets para hist chunk
- 🔲 `apps/hist-e2e/src/performance/hist-perf.spec.ts` — timing tests
- 🔲 `nx build neuroassist-ai-web` PASS dentro do budget
- 🔲 `nx e2e hist-e2e` PASS (performance smoke)

**Milestone:** Wave P8 / Performance

**Labels:**
type:performance · layer:frontend · scope:hist · priority:P8

**Acceptance criteria:**
- Build PASS com chunk hist ≤ 120 KB gzip (erro se ultrapassar).
- Playwright: skeleton visível < 100 ms após navegação para `/historico`.

**Dependencies:**
- FE-HIST-024, FE-HIST-026
