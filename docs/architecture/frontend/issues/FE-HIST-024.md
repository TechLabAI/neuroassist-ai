## [FE-HIST-024] hist performance: OnPush, trackBy, paginação e virtual scroll flag

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
Timeline renderizada com estratégia `OnPush` em todos os componentes, `@for track item.id`, paginação server-side via "carregar mais" e suporte ao flag `virtualScroll` para volumes maiores.

**Context / Problem statement:**
- SPEC-08 §2: OnPush everywhere; `@for` com `track item.id`.
- SPEC-08 §3: paginação first (default 50, max 200); "carregar mais" para próxima página.
- SPEC-08 §3: `virtualScroll` flag (default off) — ativar `CdkVirtualScrollViewport` quando flag on e volume > threshold.
- SPEC-08 §1: TTI ≤ 1.5s p75; filter switch ≤ 800ms p75; feedback round-trip ≤ 1.2s p75.
- SPEC-08 §4: sem N+1 joins client-side quando read model já resolve campos.

**Approach (execution path):**
1. Confirmar `ChangeDetectionStrategy.OnPush` em TODOS os componentes de FE-HIST-009..015.
2. `@for item of screenVm().items track item.id` no template de `HistoryTimelineComponent`.
3. `loadMore()` faz `page+1`; `#items.update(prev => [...prev, ...newItems])` — sem refetch.
4. Virtual scroll: `@if (flags.virtualScroll && items.length > virtualScrollThreshold)` → usar `cdk-virtual-scroll-viewport` com `itemSize` fixo.
5. Medir chunk size: `npx nx build neuroassist-ai-web -- --stats-json` + verificar bundle do hist chunk.

**Non-goals:**
- Não implementar date-range ou server-sent events.

**Layer(s):**
frontend / feature-timeline + data-access

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-08-performance.md` (§1, §2, §3, §4)

**Architecture alignment:**
- Standalone + OnPush · Tag Nx: `scope:hist`

**Implementation checklist (by file):**
- 🔲 Auditoria de `ChangeDetectionStrategy.OnPush` em todos os componentes
- 🔲 `@for track item.id` no template
- 🔲 Implementação de virtual scroll condicional
- 🔲 Bundle size check (< 120KB gzip)
- 🔲 `nx test hist-feature-timeline` PASS
- 🔲 `nx lint hist-feature-timeline` PASS

**Milestone:** Wave P8 / Performance

**Labels:**
type:performance · layer:frontend · scope:hist · priority:P8

**Acceptance criteria:**
- `npx nx build neuroassist-ai-web` → hist chunk ≤ 120 KB gzip.
- `@for track item.id` presente; sem trackBy por índice.
- Virtual scroll disponível quando flag on.
- Loadmore appends sem refetch das páginas já carregadas.

**Dependencies:**
- FE-HIST-008, FE-HIST-009
