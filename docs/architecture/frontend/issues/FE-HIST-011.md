## [FE-HIST-011] hist ui: EventListItemComponent (linha de timeline)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`EventListItemComponent` em `libs/hist/ui` — linha de timeline renderizando `occurredAtLabel`, `label` (rotulo humano-legível), `UrgencyBadge`, `confirmationLine`, `FeedbackAction` e (se `correctsRecordId`) link ao registro original.

**Context / Problem statement:**
- SPEC-02 §4: inputs `item: HistoryItemVm`, `marking: MarkingState`; outputs `markFeedback`, `openDetail`.
- SPEC-05 §1: anatomia da linha — `occurredAtLabel — "label" · UrgencyBadge · confirmationLine · FeedbackAction`.
- SPEC-07 §1: cada `<li>` tem accessible name composto de timestamp + label + urgency + confirmationLine.
- ADR-004: `correctsRecordId` presente → exibir link ao registro original (ambos visíveis).
- FE-GAP-13..14 (WCAG AA): accessible name e foco teclado em todos os itens.

**Approach (execution path):**
1. `EventListItemComponent` standalone, OnPush.
2. `<li>` com `[attr.aria-label]` compondo: `occurredAtLabel + ', ' + item.label + ', ' + item.urgency + ', ' + item.confirmationLine`.
3. Delega `UrgencyBadgeComponent` e `FeedbackActionComponent` (sub-issues FE-HIST-012/013).
4. Se `item.correctsRecordId` presente: renderiza marcação e link.
5. Output `openDetail` emitido em clique no item (post-MVP, guarded por `detailRoute` flag).
6. `@for` com `track item.id` no smart component pai.

**Non-goals:**
- Não implementar lógica de urgência ou feedback — FE-HIST-012/013.

**Layer(s):**
frontend / ui

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-02-component-tree.md` (§4)
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-05-ui-ux-spec.md` (§1)
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-07-accessibility.md` (§1, §5)

**Architecture alignment:**
- Standalone + OnPush · Tag Nx: `scope:hist`, `type:ui`

**Implementation checklist (by file):**
- 🔲 `libs/hist/ui/src/lib/event-list-item/event-list-item.component.ts`
- 🔲 `libs/hist/ui/src/lib/event-list-item/event-list-item.component.html`
- 🔲 `libs/hist/ui/src/lib/event-list-item/event-list-item.component.spec.ts`
- 🔲 Atualizar `libs/hist/ui/src/index.ts`
- 🔲 `nx test hist-ui` PASS
- 🔲 `nx lint hist-ui` PASS

**Milestone:** Wave P3 / Feature Timeline

**Labels:**
type:feature · layer:frontend · scope:hist · priority:P3

**Acceptance criteria:**
- `aria-label` inclui todos os 4 campos.
- Registro corretivo exibido com link ao original.
- `npx nx test hist-ui` PASS.

**Dependencies:**
- FE-HIST-009, FE-HIST-007

**Risks / Notes:**
- `confirmationLine` pode conter caracteres especiais — sanitizar ao compor `aria-label`.
