## [FE-ACS-002] Route map ACS_ROUTES[] + lazy-loading strategy

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/acs/shell-entry/src/lib/acs.routes.ts` exports `ACS_ROUTES: Routes` with the four sub-views declared
via `loadComponent`, each with its `canActivate` guard list and `title`/`data.breadcrumb`. The shell can
lazy-load the ACS module and sub-views without eagerly bundling any feature lib.

**Context / Problem statement:**
- SPEC-01 §1 defines four routed surfaces: Roster (`/acs`), CaregiverEditor novo+editar, PatientLinkage.
- Routes must share guard arrays (`authGuard + acsCoordinatorGuard`; editor routes add `acsCaregiverWriteGuard`;
  linkage route adds `acsLinkWriteGuard`) as per SPEC-01 §3.
- The `:cuidadorId` presence selects create vs edit mode in `CaregiverEditorComponent`.
- `TenantSetupComponent` and `CaregiverLinkStepComponent` are NOT routed — they are composed onboarding steps.
- Bloqueia: FE-ACS-003, FE-ACS-013, FE-ACS-016, FE-ACS-018, FE-ACS-022, FE-ACS-023

**Approach (execution path):**
1. Create `libs/acs/shell-entry/src/lib/acs.routes.ts`.
2. Declare `ACS_ROUTES: Routes` with four entries (SPEC-01 §1 master route table).
3. Each entry uses `loadComponent: () => import(...)` with the appropriate feature lib path.
4. Wire `canActivate` arrays per SPEC-01 §3 guard contracts.
5. Add `title` and `data: { breadcrumb: ... }` on each route.
6. For deep-link to unknown id: rely on BE `404` → FE error handler redirect (SPEC-07), not a route-level 404.
7. Re-export `ACS_ROUTES` from `libs/acs/shell-entry/src/index.ts`.
8. Unit test: verify route path, canActivate arrays, and loadComponent presence (no eager import).

**Non-goals:**
- No guard implementation (FE-ACS-022, FE-ACS-023).
- No component implementation (separate issues per feature).
- No onboarding step routes (those are composed, not routed).

**Layer(s):**
frontend / libs/acs/shell-entry

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/SPEC-01-route-map.md` (§1 master route table,
  §2 route composition, §3 guard contracts)

**Architecture alignment:**
- `loadComponent` per sub-view (no `loadChildren` nesting within ACS)
- `authGuard` from `@neuroassist/shared/access-decision`
- Tags Nx: `scope:acs`, `type:domain`

**Implementation checklist (by file):**

**Lib path:** `libs/acs/shell-entry/`
- 🔲 `src/lib/acs.routes.ts` — ACS_ROUTES with 4 entries + canActivate + title + breadcrumb data
- 🔲 `src/index.ts` — re-exports ACS_ROUTES

**Tests**
- 🔲 `src/lib/acs.routes.spec.ts` — route count, path assertions, canActivate arrays
- 🔲 `nx test acs-shell-entry` PASS
- 🔲 `nx lint acs-shell-entry` PASS

**Milestone:**
Wave 1 / P1 Foundation

**Labels:**
type:feature
layer:frontend
scope:acs
priority:P1

**Acceptance criteria:**
- `npx nx test acs-shell-entry` PASS
- `npx nx lint acs-shell-entry` PASS
- `npx nx build acs-shell-entry` PASS (sem erros)
- `ACS_ROUTES` tem exatamente 4 entradas (Roster, Editor-novo, Editor-editar, Linkage)
- Cada rota usa `loadComponent` (sem import estático de feature components)
- Guard arrays: roster tem `[authGuard, acsCoordinatorGuard]`; editor inclui `acsCaregiverWriteGuard`

**Dependencies:**
- FE-ACS-001 (MODULE_KEY e contrato definidos antes das rotas referenciarem esses valores)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum direto; rotas consomem os guards que por sua vez verificam permissões via `MODULE_PERMISSION_MAP`

**Testing / Evidence required:**
- Teste de shape de rotas passando
- Screenshot do router-outlet renderizando o stub do componente correto (pode ser placeholder)

**Risks / Notes:**
- O `:cuidadorId` mode-select (create vs edit) é lógica do `CaregiverEditorComponent`, não da rota — a rota
  apenas passa o param. Documenta isso no JSDoc da rota.
- Se o shell mudar o `loadChildren` para `loadRoutes`, este arquivo precisará ajuste mínimo.
