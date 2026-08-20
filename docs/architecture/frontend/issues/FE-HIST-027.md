## [FE-HIST-027] hist e2e: Playwright — feedback-verdict.feature + axe a11y gate

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
Testes Playwright para `feedback-verdict.feature` (marcação de veredicto, 409 reconcile, mark-error) e gate de acessibilidade axe-playwright (0 violations WCAG AA) implementados. Fecha FE-GAP-13..19 no nível e2e.

**Context / Problem statement:**
- E2e feature: `docs/architecture/frontend/modules/hist/e2e/feedback-verdict.feature`.
- Cenários: feedback idle→marking→marked (real), idle→marking→marked (falso_positivo), feedback mark-error + retry, 409 reconcile.
- Axe gate: saúde é domínio sensível; 0 violations AA obrigatório no CI.
- FE-GAP-13..19: todos os gaps de acessibilidade precisam de gate automático.

**Approach (execution path):**
1. `apps/hist-e2e/src/e2e/feedback-verdict.spec.ts`: mapear cada cenário Gherkin.
   - `POST /feedback` 200 → item fica `marked`; veredicto exibido com texto+ícone.
   - `POST /feedback` 5xx → `mark-error`; retry re-posts.
   - `POST /feedback` 409 → reconcile; "Já marcado por {nome} às {hora}".
   - `POST /feedback` 403 → `mark-error` com mensagem de acesso.
2. Axe gate: `import { checkA11y } from 'axe-playwright'`; executar em cada estado (loading, loaded, empty, error).
3. Configurar `axe-playwright` com regras: `wcag2a`, `wcag2aa`.
4. Rodar como parte do `nx e2e hist-e2e`.

**Non-goals:**
- Não testar lógica de Facade — coberto em unit tests.

**Layer(s):**
frontend / e2e

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/e2e/feedback-verdict.feature`
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-07-accessibility.md`

**Architecture alignment:**
- Playwright e2e · `apps/hist-e2e/`

**Implementation checklist (by file):**
- 🔲 `apps/hist-e2e/src/e2e/feedback-verdict.spec.ts`
- 🔲 `apps/hist-e2e/src/support/axe-gate.ts` — helper `checkA11y`
- 🔲 `apps/hist-e2e/package.json` — adicionar `axe-playwright` dev dependency
- 🔲 `nx e2e hist-e2e --testFile=feedback-verdict.spec.ts` PASS

**Milestone:** Wave P9 / e2e + Hardening

**Labels:**
type:test · type:accessibility · layer:frontend · scope:hist · priority:P9

**Acceptance criteria:**
- Todos os cenários de `feedback-verdict.feature` cobertos.
- axe-playwright: 0 violations WCAG AA nos 4 estados do timeline.
- `npx nx e2e hist-e2e` PASS completo.

**Dependencies:**
- FE-HIST-013, FE-HIST-019, FE-HIST-020, FE-HIST-021, FE-HIST-022, FE-HIST-026

**Risks / Notes:**
- `axe-playwright` requer playwright versão compatível — verificar antes de instalar.
- Violations de contraste podem requerer ajuste de tokens (FE-HIST-022).
