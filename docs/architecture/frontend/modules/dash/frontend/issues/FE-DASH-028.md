## [FE-DASH-028] e2e Playwright: bootstrap register nav+outlet (G6)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`apps/dash-e2e/src/bootstrap-lifecycle.spec.ts` verifica que ao montar o DashModule: item "Status" aparece na sidebar, a região `dash.global-interrupt` é registrada e a operação é idempotente (chamada dupla não duplica). Fecha coverage gap G6.

**Context / Problem statement:**
- Coverage gap G6: "bootstrap register nav+outlet" — verificar que o registro de nav e outlet acontece corretamente no bootstrap.
- Fonte: SPEC-06 §1/§3; coverage gap G6.

**Approach (execution path):**
1. Criar `apps/dash-e2e/src/bootstrap-lifecycle.spec.ts`.
2. Cenário bootstrap: navegar para `/` → verificar que "Status" está na sidebar como item ativo.
3. Cenário outlet: verificar que o container da região `dash.global-interrupt` existe no DOM (ex: via `data-outlet-region="dash.global-interrupt"`).
4. Cenário idempotência: simular chamada dupla via JS `DashShellIntegrationService.registerWithShell()` → verificar que o item "Status" aparece apenas uma vez na sidebar.

**Non-goals:**
- Não testar o conteúdo do outlet (EVNT é o dono).

**Layer(s):**
frontend / apps/dash-e2e (type:e2e)

**Bounded context:**
dash

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-06-shell-integration.md` (§1/§3)

**Architecture alignment:**
- Playwright; `scope:dash`, `type:e2e`

**Implementation checklist (by file):**

**App: apps/dash-e2e**
- 🔲 `apps/dash-e2e/src/bootstrap-lifecycle.spec.ts` (cenários G6 + G7 combinados)

**Tests**
- 🔲 `npx nx e2e dash-e2e --spec bootstrap-lifecycle` PASS
- 🔲 `npx nx lint dash-e2e` PASS

**Milestone:**
Wave 9 / e2e + Hardening

**Labels:**
type:e2e
layer:frontend
scope:dash
priority:P9
coverage-gap:G6

**Acceptance criteria:**
- `npx nx e2e dash-e2e --spec bootstrap-lifecycle` PASS
- "Status" na sidebar após bootstrap (G6)
- `data-outlet-region="dash.global-interrupt"` presente no DOM (G6)
- Registro idempotente — "Status" aparece uma única vez

**Dependencies:**
- FE-DASH-019 (dash.bootstrap.ts)
- FE-DASH-018 (DashShellIntegrationService)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Screenshot: sidebar com "Status" como primeiro item

**Risks / Notes:**
- Combinar G6 e G7 no mesmo spec file para economizar setup/teardown de app.
