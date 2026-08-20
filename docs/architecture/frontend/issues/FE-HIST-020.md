## [FE-HIST-020] hist a11y: ARIA semantics (role=list, tablist, aria-live, role=alert) — FE-GAP-13..17

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
Todos os elementos interativos e regiões de status da timeline têm semântica ARIA correta (WCAG 2.1 AA), fechando FE-GAP-13, 14, 15, 16, 17 identificados na Specs-Validate.

**Context / Problem statement:**
- FE-GAP-13: accessible name composto para cada `<li>` (timestamp + label + urgency + confirmationLine).
- FE-GAP-14: `role="tablist"` / `role="tab"` / `aria-selected` nas abas de período.
- FE-GAP-15: `aria-live="polite"` para transições de status (loaded/empty/error/feedback marking/marked).
- FE-GAP-16: `role="alert"` (assertive) para falhas de carregamento da lista.
- FE-GAP-17: skeleton com `aria-hidden="true"` e `aria-busy` no list region.
- Esta é uma plataforma de saúde — acessibilidade não é opcional.
- SPEC-07 §1/§3/§4: especificações completas de ARIA.

**Approach (execution path):**
1. `HistoryTimelineComponent`:
   - `<ul role="list" [attr.aria-busy]="status()==='loading'">` — list region.
   - `<div aria-live="polite" id="hist-status-region">` — anuncia transições.
   - `<div role="alert">` — container para erros de carregamento.
2. `FilterTabsComponent`:
   - `<div role="tablist">` / botões com `role="tab"`, `[attr.aria-selected]`, `[attr.aria-controls]="'hist-list'"`
3. `EventListItemComponent`:
   - `<li [attr.aria-label]="composedLabel()">` onde `composedLabel()` retorna string composta.
4. `ListSkeletonComponent`: `aria-hidden="true"` em cada row shimmer.
5. `FeedbackActionComponent`:
   - `aria-live="polite"` anuncia estados de marking/marked/mark-error.
6. Unit tests de acessibilidade com `@angular/cdk/testing` + aria attribute assertions.

**Non-goals:**
- Não implementar focus management avançado — FE-HIST-021.
- Não validar contraste — FE-HIST-022.

**Layer(s):**
frontend / feature-timeline + ui

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-07-accessibility.md` (§1, §4, §5)

**Architecture alignment:**
- Standalone + OnPush · Acessibilidade WCAG 2.1 AA obrigatória

**Implementation checklist (by file):**
- 🔲 Atualizar `history-timeline.component.html` — aria-busy, aria-live, role=alert
- 🔲 Atualizar `filter-tabs.component.html` — role=tablist/tab, aria-selected, aria-controls
- 🔲 Atualizar `event-list-item.component.html` — aria-label composto
- 🔲 Atualizar `list-skeleton.component.html` — aria-hidden
- 🔲 Atualizar `feedback-action.component.html` — aria-live states
- 🔲 Spec tests: verificar atributos ARIA presentes nos estados corretos
- 🔲 `nx test hist-feature-timeline` PASS
- 🔲 `nx test hist-ui` PASS

**Milestone:** Wave P6 / Accessibility (HIGHEST PRIORITY)

**Labels:**
type:accessibility · layer:frontend · scope:hist · priority:P6

**Acceptance criteria:**
- `role="list"` com `aria-busy=true` durante loading.
- `aria-live="polite"` anuncia: N registros carregados / Nenhum evento / Não foi possível carregar.
- `role="alert"` presente no container de erro de carregamento.
- `aria-selected="true"` na aba de período ativa.
- Cada `<li>` tem `aria-label` composto.
- Axe-core no Playwright: 0 violations nível AA.

**Dependencies:**
- FE-HIST-009, FE-HIST-010, FE-HIST-011, FE-HIST-014

**Risks / Notes:**
- Health platform: qualquer regressão de acessibilidade bloqueia promote.
- Considerar Axe-playwright gate no CI (FE-HIST-027).
