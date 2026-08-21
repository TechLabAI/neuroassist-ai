## [FE-HIST-014] hist ui: ListSkeletonComponent, EmptyStateComponent, LoadMoreComponent

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
Três componentes utilitários de timeline implementados em `libs/hist/ui`: `ListSkeletonComponent` (shimmer de carregamento), `EmptyStateComponent` (estado vazio reassuring), `LoadMoreComponent` (paginação).

**Context / Problem statement:**
- SPEC-02 §4: contratos de inputs/outputs para cada componente.
- SPEC-05 §2: `loading` → skeleton; `empty` → "Nenhum evento neste período — ótimo sinal".
- SPEC-07 §5: skeleton com `aria-hidden="true"` e `aria-busy` no list region.
- SPEC-08 §5: skeleton aparece dentro de 100 ms da navegação.
- `LoadMoreComponent`: output `loadMore`; input opcional `busy?: boolean`.
- Após LoadMore, foco move para o primeiro novo item (SPEC-07 §3).

**Approach (execution path):**
1. `ListSkeletonComponent`: `rows = input<number>(5)`; shimmer rows `aria-hidden`; `aria-busy` passado via attribute binding ao list region (responsabilidade do smart parent).
2. `EmptyStateComponent`: input `period: Period`; copy: "Nenhum evento neste período — ótimo sinal".
3. `LoadMoreComponent`: output `loadMore = output<void>()`; input `busy = input<boolean>(false)`; `[disabled]` quando busy; `aria-live` anuncia novos registros.

**Non-goals:**
- `InlineErrorComponent` pertence a `@neuroassist/shared/ui-feedback`, não recriar.

**Layer(s):**
frontend / ui

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-02-component-tree.md` (§4)
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-07-accessibility.md` (§3, §5)
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-08-performance.md` (§5)

**Architecture alignment:**
- Standalone + OnPush · Tag Nx: `scope:hist`, `type:ui`

**Implementation checklist (by file):**
- 🔲 `libs/hist/ui/src/lib/list-skeleton/list-skeleton.component.ts`
- 🔲 `libs/hist/ui/src/lib/empty-state/empty-state.component.ts`
- 🔲 `libs/hist/ui/src/lib/load-more/load-more.component.ts`
- 🔲 Specs para cada componente
- 🔲 Atualizar `libs/hist/ui/src/index.ts`
- 🔲 `nx test hist-ui` PASS
- 🔲 `nx lint hist-ui` PASS

**Milestone:** Wave P3 / Feature Timeline

**Labels:**
type:feature · layer:frontend · scope:hist · priority:P3

**Acceptance criteria:**
- Skeleton aparece imediatamente na navegação (< 100ms, testado via Playwright SPEC-08).
- Empty state exibe copy correto para cada `period`.
- LoadMore emite `loadMore` e fica disabled enquanto `busy=true`.
- Skeleton rows são `aria-hidden`.

**Dependencies:**
- FE-HIST-009
