## [FE-HIST-001] hist shell-entry: contract, routes e module

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
A lib `libs/hist/shell-entry` está criada com `hist.contract.ts`, `hist.routes.ts` e `HistModule`, permitindo ao Shell lazy-carregar o módulo em `/historico`.

**Context / Problem statement:**
- SPEC-01 §1: Shell faz `loadChildren: () => import('@neuroassist/hist').then(m => m.HistModule)`.
- Rota raiz `/historico` → `HistoryTimelineComponent`; detail `/historico/:eventId` gated pela flag `detailRoute` (default off).
- Bloqueia: FE-HIST-002, FE-HIST-003, FE-HIST-009 e toda a wave P3.

**Approach (execution path):**
1. Criar `libs/hist/shell-entry` com `project.json` Nx (tags `scope:hist, type:app`).
2. Implementar `hist.contract.ts` — exportar token `HIST_ROUTES` e tipos públicos.
3. Implementar `hist.routes.ts` — rota raiz + detail gated por flag (SPEC-01 §1).
4. Implementar `HistModule` (re-export ou standalone bootstrap) exportando as rotas.
5. Implementar `hist.bootstrap.ts` — inicialização, registra `HistShellIntegrationService`.
6. Boundary rule: `scope:hist` depende apenas de `scope:hist` + `scope:shared`.

**Non-goals:**
- Não implementar guardas — FE-HIST-016.
- Não implementar componentes de feature — wave P3.

**Layer(s):**
frontend / shell-entry

**Bounded context:**
hist

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-01-route-map.md` (§1, §2)
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-02-component-tree.md` (§1)

**Architecture alignment:**
- Padrão tax-map · Standalone + OnPush + Signals
- Tag Nx: `scope:hist`, `type:app`

**Implementation checklist (by file):**

**libs/hist/shell-entry**
- 🔲 `libs/hist/shell-entry/src/index.ts`
- 🔲 `libs/hist/shell-entry/src/lib/hist.contract.ts`
- 🔲 `libs/hist/shell-entry/src/lib/hist.routes.ts`
- 🔲 `libs/hist/shell-entry/src/lib/hist.module.ts`
- 🔲 `libs/hist/shell-entry/src/lib/hist.bootstrap.ts`
- 🔲 `libs/hist/shell-entry/project.json`

**Tests**
- 🔲 `nx test hist-shell-entry` PASS
- 🔲 `nx lint hist-shell-entry` PASS (sem boundary violations)

**Milestone:** Wave P1 / Foundation

**Labels:**
type:feature · layer:frontend · scope:hist · priority:P1

**Acceptance criteria:**
- `npx nx test hist-shell-entry` PASS
- `npx nx lint hist-shell-entry` PASS
- Shell pode referenciar `@neuroassist/hist`; lazy-chunk compila.
- `detailRoute` flag off → rota `/historico/:eventId` não registrada.

**Dependencies:**
- Nenhuma (primeira issue da wave P1)

**API / Contract impact:**
- Nenhum nesta issue.

**Risks / Notes:**
- Se prototype-first (D-493), shell-entry pode já existir parcialmente — reconciliar antes de criar.
