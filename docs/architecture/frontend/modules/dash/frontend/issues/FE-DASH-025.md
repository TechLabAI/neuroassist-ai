## [FE-DASH-025] e2e Playwright: chromeless /entrada (G3)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`apps/dash-e2e/src/entrada-chromeless.spec.ts` verifica que `/entrada` renderiza sem sidebar, sem topbar e sem breadcrumb, e que a sessão válida redireciona para `/`. Fecha coverage gap G3.

**Context / Problem statement:**
- Coverage gap G3: `/entrada` deve ser chromeless (sem sidebar, sem topbar, sem breadcrumb).
- Fonte: SPEC-01 §1/§2; coverage gap G3.

**Approach (execution path):**
1. Criar `apps/dash-e2e/src/entrada-chromeless.spec.ts`.
2. Cenário 1: Usuário não autenticado navega para `/entrada` → sidebar e topbar AUSENTES no DOM.
3. Cenário 2: Usuário autenticado navega para `/entrada` → redirect imediato para `/`.
4. Verificar que breadcrumb container não está presente quando `/entrada` está ativo.
5. Usar `page.route()` para mockar auth.

**Non-goals:**
- Não testar o fluxo de login completo.

**Layer(s):**
frontend / apps/dash-e2e (type:e2e)

**Bounded context:**
dash

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-01-route-map.md` (§1/§2 — chromeless)

**Architecture alignment:**
- Playwright; `scope:dash`, `type:e2e`

**Implementation checklist (by file):**

**App: apps/dash-e2e**
- 🔲 `apps/dash-e2e/src/entrada-chromeless.spec.ts`

**Tests**
- 🔲 `npx nx e2e dash-e2e --spec entrada-chromeless` PASS
- 🔲 `npx nx lint dash-e2e` PASS

**Milestone:**
Wave 9 / e2e + Hardening

**Labels:**
type:e2e
layer:frontend
scope:dash
priority:P9
coverage-gap:G3

**Acceptance criteria:**
- `npx nx e2e dash-e2e --spec entrada-chromeless` PASS
- `/entrada` sem sidebar, sem topbar, sem breadcrumb (G3)
- Sessão válida em `/entrada` → redirect para `/`

**Dependencies:**
- FE-DASH-009 (EntradaPageComponent)
- FE-DASH-004 (shell registration — layout chromeless)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Screenshot: `/entrada` sem chrome do shell

**Risks / Notes:**
- O chromeless pode ser implementado via layout diferente no shell; verificar que os seletores CSS/Playwright capturam a ausência de sidebar e topbar corretamente.
