## [FE-HIST-021] hist a11y: keyboard operation e focus management — FE-GAP-18

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
Todos os controles interativos da timeline são operáveis por teclado em ordem lógica; foco é gerenciado corretamente após LoadMore, após fechar seleção de veredicto e após retry. Fecha FE-GAP-18.

**Context / Problem statement:**
- SPEC-07 §3: Tab order lógico; Arrow keys nas abas; `Esc` fecha seleção de veredicto e retorna foco ao trigger.
- SPEC-07 §3: após "carregar mais", foco move para o primeiro novo item (ou `aria-live` anuncia "N novos registros").
- FE-GAP-18 (Specs-Validate): cenários de teclado ausentes nas specs — esta issue fecha o gap.
- `FeedbackActionComponent`: grupo de botões `real`/`falso_positivo` é navegável por teclado; `Esc` cancela.

**Approach (execution path):**
1. `FilterTabsComponent`:
   - `keydown` handler: Arrow Left/Right move `aria-selected` + foco entre abas.
   - Home/End vão para primeira/última aba.
2. `FeedbackActionComponent`:
   - Botão "Foi real?" abre grupo de escolha; foco vai para primeiro botão de escolha.
   - `keydown.escape` fecha e retorna foco ao trigger (ref via `ElementRef` ou template ref).
3. `LoadMoreComponent`:
   - Após append, `HistoryTimelineComponent` usa `ViewChildren` ou `afterNextRender` para mover foco ao primeiro item adicionado.
   - Alternativa: `aria-live` anuncia "N novos registros" (polite).
4. E2e Playwright: testar fluxo completo com `.keyboard` API.

**Non-goals:**
- Não implementar ARIA semantics — FE-HIST-020.
- Não implementar hit targets — FE-HIST-022.

**Layer(s):**
frontend / ui + feature-timeline

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-07-accessibility.md` (§3, §5)

**Architecture alignment:**
- Standalone + OnPush

**Implementation checklist (by file):**
- 🔲 Atualizar `filter-tabs.component.ts` — Arrow key handler
- 🔲 Atualizar `feedback-action.component.ts` — Esc handler + focus return
- 🔲 Atualizar `history-timeline.component.ts` — focus-first-new-item após loadMore
- 🔲 `filter-tabs.component.spec.ts` — teclado
- 🔲 `feedback-action.component.spec.ts` — Esc + focus
- 🔲 `nx test hist-ui` PASS
- 🔲 `nx test hist-feature-timeline` PASS

**Milestone:** Wave P6 / Accessibility (HIGHEST PRIORITY)

**Labels:**
type:accessibility · layer:frontend · scope:hist · priority:P6

**Acceptance criteria:**
- Arrow keys navegam abas; foco visível em cada aba.
- `Esc` fecha seleção de veredicto; foco retorna ao botão "Foi real?".
- LoadMore: foco no primeiro item novo (ou aria-live anuncia "N novos registros").
- Playwright keyboard test PASS (FE-HIST-026).

**Dependencies:**
- FE-HIST-013, FE-HIST-010, FE-HIST-014
