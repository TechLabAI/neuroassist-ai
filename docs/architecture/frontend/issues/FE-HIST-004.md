## [FE-HIST-004] hist navigation: breadcrumb state

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/hist/navigation` produz `BreadcrumbItem[]` para o Shell: `Início › Histórico` (+ `› <evento>` quando detail route ativa).

**Context / Problem statement:**
- SPEC-01 §4: breadcrumb `Início › Histórico`; `› <evento>` no detail route.
- SPEC-02 §1: lib `libs/hist/navigation` com tags `scope:hist, type:domain`.
- O breadcrumb precisa ser populado reativamente via signal quando o `eventId` muda.

**Approach (execution path):**
1. Criar `libs/hist/navigation` com `project.json` Nx (tags `scope:hist, type:domain`).
2. `HistBreadcrumbService` — expõe `breadcrumb: Signal<BreadcrumbItem[]>`.
3. Registra `Início › Histórico` sempre; adiciona `› <evento>` quando detail route ativa e `eventId` presente.
4. `HistoryTimelineComponent` injeta o serviço e repassa ao Shell via output/slot de breadcrumb.

**Non-goals:**
- Não implementar o componente de breadcrumb do Shell (pertence a `@neuroassist/shared`).

**Layer(s):**
frontend / navigation

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-01-route-map.md` (§4)

**Architecture alignment:**
- Tag Nx: `scope:hist`, `type:domain`

**Implementation checklist (by file):**
- 🔲 `libs/hist/navigation/src/lib/hist-breadcrumb.service.ts`
- 🔲 `libs/hist/navigation/src/index.ts`
- 🔲 `libs/hist/navigation/project.json`
- 🔲 `nx test hist-navigation` PASS
- 🔲 `nx lint hist-navigation` PASS

**Milestone:** Wave P1 / Foundation

**Labels:**
type:feature · layer:frontend · scope:hist · priority:P1

**Acceptance criteria:**
- Breadcrumb exibe `Início › Histórico` em `/historico`.
- Em `/historico/:eventId` (flag on) exibe `Início › Histórico › <evento>`.

**Dependencies:**
- FE-HIST-003
