## [FE-DASH-014] NotificationFilterBarComponent + NotificationListComponent + NotificationListItemComponent — ui apresentacionais

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/dash/ui` contém três componentes presentacionais para o Notification Center: `NotificationFilterBarComponent` (filtros recent/unread), `NotificationListComponent` (lista de items) e `NotificationListItemComponent` (item repetido com tier badge, read state, timestamp).

**Context / Problem statement:**
- Todos são puros: input/output, sem HTTP, sem facade.
- `NotificationListItemComponent` exibe badge de tier (`discreet`), estado de leitura e timestamp formatado (pt-BR).
- Fonte: SPEC-02 (feature-notifications).

**Approach (execution path):**
1. `NotificationFilterBarComponent` (standalone, OnPush):
   - `input()`: `filter: NotificationFilterVm`.
   - `output()`: `filterChange: OutputEmitterRef<NotificationFilterVm>`.
   - Botões/tabs "Recentes" / "Não lidos".
2. `NotificationListComponent` (standalone, OnPush):
   - `input()`: `items: readonly NotificationItemVm[]`.
   - `output()`: `markRead: OutputEmitterRef<string>` (dispatchId), `open: OutputEmitterRef<string>`.
   - Itera sobre items usando `@for` e projeta `NotificationListItemComponent`.
3. `NotificationListItemComponent` (standalone, OnPush):
   - `input()`: `item: NotificationItemVm`.
   - `output()`: `markRead`, `open`.
   - Badge de tier (discreet ≠ full_screen_alarm), ícone de lido/não-lido, timestamp relativo.
4. Adicionar ao barrel `index.ts` de `dash/ui`.

**Non-goals:**
- Não renderizar alarm overlay.
- Não gerenciar estado.

**Layer(s):**
frontend / libs/dash/ui (type:ui)

**Bounded context:**
dash

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-02-component-hierarchy.md` (feature-notifications)
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-03-view-models.md` (§2 NotificationItemVm)

**Architecture alignment:**
- Standalone + OnPush + Signals
- Tag Nx: `scope:dash`, `type:ui`

**Implementation checklist (by file):**

**Lib: libs/dash/ui**
- 🔲 `libs/dash/ui/src/lib/notification-filter-bar/notification-filter-bar.component.ts`
- 🔲 `libs/dash/ui/src/lib/notification-list/notification-list.component.ts`
- 🔲 `libs/dash/ui/src/lib/notification-list-item/notification-list-item.component.ts`
- 🔲 `libs/dash/ui/src/index.ts` (barrel — adicionar exports)

**Tests**
- 🔲 `npx nx test dash-ui` PASS (testar: filterChange emitido ao clicar; markRead emitido com dispatchId correto; full_screen_alarm renderiza sem botão de ação)
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
- `filterChange` emite o filtro correto ao alternar tabs
- `markRead` emite `dispatchId` correto
- `full_screen_alarm` item não tem botão de ação (read-only)

**Dependencies:**
- FE-DASH-013 (NotificationCenterPageComponent usa estes componentes)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Screenshots: item discreet lido, item discreet não-lido, item full_screen_alarm

**Risks / Notes:**
- Timestamp relativo (ex: "há 5 min") — usar pipe Angular DatePipe ou intl-relative; não hardcodar locale.
