## [FE-DASH-015] EmptyOrErrorStateComponent — estado inline vazio/erro (ui)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/dash/ui/src/lib/empty-or-error-state/empty-or-error-state.component.ts` — componente presentacional que renderiza estado de lista vazia ou erro inline, nunca bloqueando o Home (coverage gap G5).

**Context / Problem statement:**
- Usado pelo `NotificationCenterPageComponent` quando `items` está vazio ou `state='error'`.
- Nunca substituir o Home inteiro — é sempre inline, dentro do contexto da lista.
- Fonte: SPEC-02 (feature-notifications); SPEC-03 §2.
- Cobre coverage gap G5: "lista vazia/erro inline".

**Approach (execution path):**
1. `EmptyOrErrorStateComponent` (standalone, OnPush):
   - `input()`: `kind: 'empty' | 'error'`, `message: string` (não-técnico, pt-BR).
   - Template: ícone + mensagem descritiva; no caso de 'error', botão opcional "Tentar novamente" com `output: retry`.
2. Usar tokens de cor de `@neuroassist/shared/ui-tokens`.
3. Adicionar ao barrel `index.ts` de `dash/ui`.

**Non-goals:**
- Não bloquear o shell chrome.
- Não ser modal.

**Layer(s):**
frontend / libs/dash/ui (type:ui)

**Bounded context:**
dash

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (feature-notifications → EmptyOrErrorStateComponent)
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-03-view-models.md` (§2 invariants)

**Architecture alignment:**
- Standalone + OnPush + Signals
- Tag Nx: `scope:dash`, `type:ui`

**Implementation checklist (by file):**

**Lib: libs/dash/ui**
- 🔲 `libs/dash/ui/src/lib/empty-or-error-state/empty-or-error-state.component.ts`
- 🔲 `libs/dash/ui/src/lib/empty-or-error-state/empty-or-error-state.component.html`
- 🔲 `libs/dash/ui/src/index.ts` (barrel)

**Tests**
- 🔲 `npx nx test dash-ui` PASS (testar: kind='empty' renderiza sem botão retry por default; kind='error' renderiza com retry output; mensagem renderizada)
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
- Renderizado inline (não modal, não full-screen)
- `kind='empty'` mostra mensagem sem retry
- `kind='error'` mostra mensagem + botão retry (coverage gap G5)

**Dependencies:**
- FE-DASH-013 (NotificationCenterPageComponent)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Screenshot: lista vazia e lista em erro — ambos inline

**Risks / Notes:**
- Este componente pode ser reutilizado por outras feature-libs no futuro (ex: HIST); manter o input genérico (`kind` + `message`).
