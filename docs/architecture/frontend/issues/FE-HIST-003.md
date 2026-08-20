## [FE-HIST-003] hist integration: descriptor de shell e HistShellIntegrationService

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/hist/integration` expõe `HistShellIntegrationService` e `hist.descriptor.ts`, registrando o módulo HIST no Shell (sidebar "Histórico", lazy-route, nav highlight).

**Context / Problem statement:**
- SPEC-01 §4: `HistShellIntegrationService` registra entrada de sidebar e lazy-route com o Shell.
- `hist.descriptor.ts` referenciado em `apps/shell/src/app/config/tool-descriptors.ts`.
- SPEC-02 §1: lib `libs/hist/integration` com tags `scope:hist, type:domain`.
- Bloqueia: FE-HIST-004 (navigation/breadcrumb).

**Approach (execution path):**
1. Criar `libs/hist/integration` com `project.json` Nx (tags `scope:hist, type:domain`).
2. `HistShellIntegrationService` — método `register()`: adiciona rota `/historico` e entrada "Histórico" na sidebar.
3. `hist.descriptor.ts` — `HistDescriptor`: `id`, `label`, `route`, `icon`, `permissions`.
4. Chamar `register()` em `hist.bootstrap.ts` (FE-HIST-001).
5. Adicionar entrada em `apps/shell/src/app/config/tool-descriptors.ts`.

**Non-goals:**
- Não implementar breadcrumb — FE-HIST-004.

**Layer(s):**
frontend / integration

**Service execution spec(s):**
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-01-route-map.md` (§4)
- `docs/architecture/frontend/modules/hist/frontend-spec/SPEC-02-component-tree.md` (§1)

**Architecture alignment:**
- Tag Nx: `scope:hist`, `type:domain`

**Implementation checklist (by file):**
- 🔲 `libs/hist/integration/src/lib/hist-shell-integration.service.ts`
- 🔲 `libs/hist/integration/src/lib/hist.descriptor.ts`
- 🔲 `libs/hist/integration/src/index.ts`
- 🔲 `libs/hist/integration/project.json`
- 🔲 `apps/shell/src/app/config/tool-descriptors.ts` — entrada `hist`
- 🔲 `nx test hist-integration` PASS
- 🔲 `nx lint hist-integration` PASS

**Milestone:** Wave P1 / Foundation

**Labels:**
type:feature · layer:frontend · scope:hist · priority:P1

**Acceptance criteria:**
- Sidebar exibe "Histórico" e fica ativa em `/historico`.
- Descriptor registrado em `tool-descriptors.ts`.

**Dependencies:**
- FE-HIST-001

**Risks / Notes:**
- Verificar padrão de entry existente em `tool-descriptors.ts` (outros módulos).
