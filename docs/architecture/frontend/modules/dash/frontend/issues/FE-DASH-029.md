## [FE-DASH-029] e2e Playwright: destroy SSE + region release (G7)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`apps/dash-e2e/src/bootstrap-lifecycle.spec.ts` (cenários adicionais) verifica que ao destruir o DashModule: o EventSource SSE é fechado e a região `dash.global-interrupt` é deregistrada. Fecha coverage gap G7.

**Context / Problem statement:**
- Coverage gap G7: "destroy SSE+region" — garantir que o lifecycle de destruição limpa recursos corretamente.
- Um SSE não fechado causa vazamento de memória e de sessão no BE (`PanelSession` nunca eviccionado).
- Fonte: SPEC-06 §3; SPEC-04 §4 (SSE lifecycle); coverage gap G7.

**Approach (execution path):**
1. Adicionar cenários ao `apps/dash-e2e/src/bootstrap-lifecycle.spec.ts`.
2. Cenário SSE destroy: montar DASH → verificar EventSource aberto (via `page.evaluate()`); destruir módulo (navegar para rota fora do DASH) → verificar que EventSource está fechado.
3. Cenário region release: após destroy → verificar que `data-outlet-region="dash.global-interrupt"` não está mais no DOM.
4. Cenário sem exceção: destroy com SSE nunca aberto (não autenticado) → não lança exceção.

**Non-goals:**
- Não testar o cleanup do BE (PanelSession eviction — escopo de BE).

**Layer(s):**
frontend / apps/dash-e2e (type:e2e)

**Bounded context:**
dash

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-06-shell-integration.md` (§3)

**Architecture alignment:**
- Playwright; `scope:dash`, `type:e2e`

**Implementation checklist (by file):**

**App: apps/dash-e2e**
- 🔲 `apps/dash-e2e/src/bootstrap-lifecycle.spec.ts` (cenários G7 adicionados ao mesmo arquivo de G6)

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
coverage-gap:G7

**Acceptance criteria:**
- `npx nx e2e dash-e2e --spec bootstrap-lifecycle` PASS
- Após destroy: EventSource fechado (G7)
- Após destroy: outlet region removida do DOM (G7)
- Destroy sem SSE aberto: sem exceção

**Dependencies:**
- FE-DASH-019 (dash.bootstrap.ts — destroy)
- FE-DASH-008 (DashStreamClient — close())
- FE-DASH-028 (mesmo spec file)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Evidência via `page.evaluate()` do estado do EventSource após destroy

**Risks / Notes:**
- Verificar EventSource via `page.evaluate(() => window.__dashEventSource?.readyState)` — pode precisar de exposição da referência no window para testes (apenas em mode de teste, nunca em produção).
