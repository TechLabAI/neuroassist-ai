## [FE-DASH-013] NotificationCenterPageComponent — container smart (feature-notifications)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/dash/feature-notifications` contém `NotificationCenterPageComponent` (smart, OnPush) que injeta `NotificationsFacade`, lê `items()`, `state()` e `unreadCount()`, e compõe `NotificationFilterBarComponent`, `NotificationListComponent` e `EmptyOrErrorStateComponent`.

**Context / Problem statement:**
- Rota `/notificacoes` (authGuard); breadcrumb `Status › Notificações`.
- Erros da lista renderizam estado inline (`EmptyOrErrorStateComponent`) e **nunca** bloqueiam o Home (SPEC-03 §2).
- Fonte: SPEC-02 (feature-notifications); SPEC-01 §2.
- Bloqueia: FE-DASH-014..015, FE-DASH-028

**Approach (execution path):**
1. Criar lib Nx `dash/feature-notifications` (type:feature, scope:dash).
2. `NotificationCenterPageComponent` injeta `NotificationsFacade` via `inject()`.
3. Lê signals: `items()`, `state()`, `unreadCount()`, `filter()`.
4. Projeta `NotificationFilterBarComponent` (input: filter, output: filterChange → `facade.setFilter()`).
5. Projeta `NotificationListComponent` (input: items, outputs: markRead → `facade.markRead()`, open → `facade.open()`).
6. Quando `state='error'` ou `items` vazio: projeta `EmptyOrErrorStateComponent` inline.
7. Breadcrumb: configura via `data.breadcrumb = 'Notificações'` na rota (FE-DASH-002).

**Non-goals:**
- Não renderizar o alarm overlay (EVNT).
- Não importar scope:evnt.

**Layer(s):**
frontend / libs/dash/feature-notifications (type:feature)

**Bounded context:**
dash

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (feature-notifications)
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-03-view-models.md` (§2)

**Architecture alignment:**
- Standalone + OnPush + Signals
- Tag Nx: `scope:dash`, `type:feature`

**Implementation checklist (by file):**

**Lib: libs/dash/feature-notifications**
- 🔲 `libs/dash/feature-notifications/src/lib/notification-center-page/notification-center-page.component.ts`
- 🔲 `libs/dash/feature-notifications/src/lib/notification-center-page/notification-center-page.component.html`
- 🔲 `libs/dash/feature-notifications/src/index.ts` (barrel)

**Tests**
- 🔲 `npx nx test dash-feature-notifications` PASS (testar: markRead chama facade; state='error' renderiza EmptyOrErrorState; items vazio renderiza EmptyOrErrorState; full_screen_alarm aparece como entry read-only)
- 🔲 `npx nx lint dash-feature-notifications` PASS

**Milestone:**
Wave 3 / Feature Libs

**Labels:**
type:feature
layer:frontend
scope:dash
priority:P3

**Acceptance criteria:**
- `npx nx test dash-feature-notifications` PASS
- `npx nx lint dash-feature-notifications` PASS
- `npx nx build dash-feature-notifications` PASS
- Lista vazia renderiza `EmptyOrErrorStateComponent` (coverage gap G5)
- Estado de erro renderiza inline e não bloqueia Home
- `full_screen_alarm` aparece como entry read-only na lista

**Dependencies:**
- FE-DASH-007 (NotificationsFacade)
- FE-DASH-014 (NotificationFilterBarComponent, NotificationListComponent)
- FE-DASH-015 (EmptyOrErrorStateComponent)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Consome facade que acessa `/panel/notifications/**`

**Testing / Evidence required:**
- Screenshots: lista preenchida, lista vazia, estado de erro

**Risks / Notes:**
- `full_screen_alarm` entries são read-only; garantir que nenhum botão de "tomar ação" apareça na lista para esse tier (ação é exclusiva do EVNT overlay).
