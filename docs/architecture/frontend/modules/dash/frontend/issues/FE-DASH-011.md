## [FE-DASH-011] StatusCardComponent — ui apresentacional (calm/loading/error + retry + viewHistory)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/dash/ui/src/lib/status-card/status-card.component.ts` — componente presentacional puro que recebe `vm: HomeStatusCardVm` e emite `retry` e `viewHistory`, renderizando três kinds: `calm` (verde), `loading` (skeleton), `error` (banner com retry).

**Context / Problem statement:**
- É o elemento visual central do Home; nunca renderiza alarm styling (EVNT é o dono do overlay).
- Deve renderizar loading state com skeleton enquanto `state='loading'` (coverage gap G4).
- `kind='calm'` só deve aparecer enquanto `calm()` for true.
- Fonte: SPEC-02 (feature-home); SPEC-03 §1 (HomeStatusCardVm).
- Bloqueia: FE-DASH-023 (loading state polish), FE-DASH-027 (e2e)

**Approach (execution path):**
1. Criar `StatusCardComponent` em `libs/dash/ui` (standalone, OnPush).
2. `input()`: `vm: HomeStatusCardVm` (kind, patientName, lastCheckLabel, syncLabel, errorMessage).
3. `output()`: `retry: OutputEmitterRef<void>`, `viewHistory: OutputEmitterRef<void>`.
4. Template: `@switch(vm().kind)` → calm (ícone shield-check verde + labels), loading (skeleton divs), error (mensagem não-técnica pt-BR + botão "Tentar novamente").
5. Nenhum estado de alarme no template (invariante: alarme = EVNT).
6. Usar design tokens de `@neuroassist/shared/ui-tokens`.

**Non-goals:**
- Não injetar façade nem fazer HTTP.
- Não renderizar alarm/toast.

**Layer(s):**
frontend / libs/dash/ui (type:ui)

**Bounded context:**
dash

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (feature-home → StatusCardComponent)
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-03-view-models.md` (§1 HomeStatusCardVm)

**Architecture alignment:**
- Standalone + OnPush + Signals (input/output do Angular 17+)
- Tag Nx: `scope:dash`, `type:ui`

**Implementation checklist (by file):**

**Lib: libs/dash/ui**
- 🔲 `libs/dash/ui/src/lib/status-card/status-card.component.ts`
- 🔲 `libs/dash/ui/src/lib/status-card/status-card.component.html`
- 🔲 `libs/dash/ui/src/lib/status-card/status-card.component.scss`
- 🔲 `libs/dash/ui/src/index.ts` (barrel)

**Tests**
- 🔲 `npx nx test dash-ui` PASS (testar: kind='calm' renderiza shield-check; kind='loading' renderiza skeleton sem "está bem"; kind='error' renderiza mensagem + botão retry; retry output emitido ao clicar)
- 🔲 `npx nx lint dash-ui` PASS

**Milestone:**
Wave 3 / Feature Libs

**Labels:**
type:ui
layer:frontend
scope:dash
priority:P3

**Acceptance criteria:**
- `npx nx test dash-ui` PASS
- `npx nx lint dash-ui` PASS
- `npx nx build dash-ui` PASS
- `kind='loading'` renderiza skeleton e NÃO mostra "está bem" (coverage gap G4)
- `kind='error'` mostra mensagem pt-BR não-técnica e botão de retry
- `retry` output emite ao clicar no botão
- Nenhum token de cor de alarme usado

**Dependencies:**
- FE-DASH-010 (HomeStatusPageComponent consome este componente)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum (presentacional puro)

**Testing / Evidence required:**
- Screenshots dos três kinds: calm, loading, error

**Risks / Notes:**
- `kind='calm'` só deve ser renderizado quando `calm()` é true no parent — este componente não faz essa lógica, apenas renderiza o kind que recebe.
