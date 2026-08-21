## [FE-HIST-010] hist ui: FilterTabsComponent (período Hoje/Semana/Mês)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`FilterTabsComponent` em `libs/hist/ui` — barra de abas Hoje/Semana/Mês com semântica ARIA correta (tablist/tab/aria-selected/aria-controls), refletindo o `period` ativo e emitindo `periodChange`.

**Context / Problem statement:**
- SPEC-02 §4: input `period: Period`, output `periodChange: EventEmitter<Period>`.
- SPEC-07 §1: `role="tablist"` com `role="tab"` filhos, `aria-selected` na aba ativa, `aria-controls` apontando para o painel de lista.
- FE-GAP-13 (WCAG AA): abas devem ser operáveis por teclado e anunciáveis por screen-reader.
- SPEC-05 §3: trocar aba atualiza URL `?period=`, reseta página 0, mostra skeleton.

**Approach (execution path):**
1. `FilterTabsComponent` standalone, OnPush; input `period = input<Period>()`.
2. Template: `<div role="tablist">` com 3 `<button role="tab" [attr.aria-selected]="...">`.
3. `aria-controls` aponta para `id` do painel de lista em `HistoryTimelineComponent`.
4. Tab key e Arrow key navegam entre as abas (SPEC-07 §3).
5. Output `periodChange = output<Period>()`.
6. Unit test: render cada aba, verificar `aria-selected` correto.

**Non-goals:**
- Não implementar date-range picker — fora do MVP.

**Layer(s):**
frontend / ui

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-02-component-tree.md` (§4)
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-07-accessibility.md` (§1, §3)

**Architecture alignment:**
- Standalone + OnPush · Tag Nx: `scope:hist`, `type:ui`

**Implementation checklist (by file):**
- 🔲 `libs/hist/ui/src/lib/filter-tabs/filter-tabs.component.ts`
- 🔲 `libs/hist/ui/src/lib/filter-tabs/filter-tabs.component.html`
- 🔲 `libs/hist/ui/src/lib/filter-tabs/filter-tabs.component.spec.ts`
- 🔲 `libs/hist/ui/project.json` (se ainda não existir)
- 🔲 Atualizar `libs/hist/ui/src/index.ts`
- 🔲 `nx test hist-ui` PASS
- 🔲 `nx lint hist-ui` PASS

**Milestone:** Wave P3 / Feature Timeline

**Labels:**
type:feature · layer:frontend · scope:hist · priority:P3

**Acceptance criteria:**
- `aria-selected="true"` na aba do `period` ativo.
- Tabs operáveis por teclado (Arrow keys).
- `periodChange` emitido no clique/Enter de cada aba.
- `npx nx test hist-ui` PASS.

**Dependencies:**
- FE-HIST-009, FE-HIST-020 (ARIA semantics gate)
