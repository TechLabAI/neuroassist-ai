## [FE-HIST-009] hist feature-timeline: HistoryTimelineComponent (smart)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`HistoryTimelineComponent` implementado em `libs/hist/feature-timeline` — único componente smart do módulo, lê todos os signals da `HistoryFacade` e delega a presentacionais via `@if`/`@for`.

**Context / Problem statement:**
- SPEC-02 §2/§3: componente smart OnPush; lê `screenVm`, `status`, `period`, `hasMore`, `markingFor(id)`.
- Template: `@if status=loading` → `ListSkeletonComponent`; `@if status=error` → `InlineErrorComponent`; `@if status=empty` → `EmptyStateComponent`; `@if status=loaded` → lista `EventListItemComponent`.
- `HistoryTimelineComponent` **não tem estado local** — todas as mutações passam pela Facade.
- Bloqueia: FE-HIST-010..015 (todos os presentacionais dependem do contrato de comunicação smart↔presentacional).

**Approach (execution path):**
1. Criar `libs/hist/feature-timeline` com `project.json` Nx (tags `scope:hist, type:feature`).
2. `HistoryTimelineComponent` — standalone, OnPush, injecta `HistoryFacade`.
3. Template com `@if`/`@for track item.id`; importa todos os presentacionais.
4. Handlers: `onPeriodChange(p)` → `facade.setPeriod(p)`; `onMarkFeedback({id,verdict})` → `facade.markFeedback(id,verdict)`; `onLoadMore()` → `facade.loadMore()`; `onRetry()` → `facade.retry()`.
5. Registrar rota em `hist.routes.ts` (FE-HIST-001).
6. `nx test hist-feature-timeline` PASS.

**Non-goals:**
- Não implementar os presentacionais aqui — FE-HIST-010..015.

**Layer(s):**
frontend / feature-timeline

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-02-component-tree.md` (§2, §3)
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-05-ui-ux-spec.md` (§1, §2)

**Architecture alignment:**
- Standalone + OnPush + Signals · Tag Nx: `scope:hist`, `type:feature`

**Implementation checklist (by file):**
- 🔲 `libs/hist/feature-timeline/src/lib/history-timeline.component.ts`
- 🔲 `libs/hist/feature-timeline/src/lib/history-timeline.component.html`
- 🔲 `libs/hist/feature-timeline/src/lib/history-timeline.component.spec.ts`
- 🔲 `libs/hist/feature-timeline/src/index.ts`
- 🔲 `libs/hist/feature-timeline/project.json`
- 🔲 `nx test hist-feature-timeline` PASS
- 🔲 `nx lint hist-feature-timeline` PASS

**Milestone:** Wave P3 / Feature Timeline

**Labels:**
type:feature · layer:frontend · scope:hist · priority:P3

**Acceptance criteria:**
- `npx nx test hist-feature-timeline` PASS.
- Componente tem zero estado local mutável; tudo via Facade.
- `@for` usa `track item.id`.
- Screenshot states: loading (skeleton), error (inline card), empty (state), loaded (lista).

**Dependencies:**
- FE-HIST-008 (Facade)

**Risks / Notes:**
- Testar com `TestBed` + mock `HistoryFacade`; não usar a Facade real em unit tests.
