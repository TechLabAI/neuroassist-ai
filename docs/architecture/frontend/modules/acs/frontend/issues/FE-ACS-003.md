## [FE-ACS-003] ACS module entry lib skeleton + Nx library tags (apps/acs + libs/acs/shell-entry)

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`apps/acs` (thin host) and all ten `libs/acs/*` library stubs are registered in the Nx workspace with correct
`project.json` tags (`scope:acs`, `type:<domain|feature|data-access|ui>`). The barrel `@neuroassist/acs`
re-exports the public API of `libs/acs/shell-entry` so the shell can do
`import('@neuroassist/acs').then(m => m.AcsModule)`.

**Context / Problem statement:**
- `generator-config.yml` lists 10 libs and one e2e app; all must be scaffolded with correct Nx tags so boundary
  lint rules can enforce `scope:acs` isolation.
- The entry barrel `@neuroassist/acs` is the only import the shell ever makes; everything else is internal.
- Bloqueia: todos os FE-ACS-004..031 e INT-FE-ACS-001..005

**Approach (execution path):**
1. Run `nx g @nx/angular:library` for each of the 10 libs with appropriate `--tags` (generator-config.yml).
2. Create `apps/acs` thin host with `AppModule` or standalone bootstrap pointing to `ACS_ROUTES`.
3. Create `apps/acs-e2e` Playwright project (registered for P9 e2e issues).
4. Verify `@neuroassist/acs` path alias resolves to `libs/acs/shell-entry/src/index.ts`.
5. Set lint boundary rules in `.eslintrc` to enforce:
   - `scope:acs` libs cannot import `scope:pac`, `scope:edge`, `scope:gest` (FE 02 §4, ADR-006).
   - `type:ui` libs cannot import `type:feature` or `type:data-access`.
   - `type:feature` libs cannot import other `type:feature` libs within acs.
6. Run `nx lint acs-shell-entry` to verify no boundary violations.

**Non-goals:**
- No component or service code (all in subsequent issues).
- No routing logic (FE-ACS-002).

**Layer(s):**
frontend / apps/acs · libs/acs/* (all ten)

**Bounded context:**
acs

**Service execution spec(s):**
- `docs/architecture/frontend/modules/acs/frontend/frontend-spec/generator-config.yml` (libs section)
- `docs/architecture/frontend/modules/acs/frontend/02-architecture-blueprint.md` (§3 library layout)

**Architecture alignment:**
- Nx boundary rules enforce hexagonal-FE isolation (ADR-006)
- Scope tags: `scope:acs`; type tags per lib type
- All libs standalone-ready (no NgModule unless required by shell wiring)

**Implementation checklist (by file):**

**App / Libs created:**
- 🔲 `apps/acs/project.json` — thin host, tags: `scope:acs,type:app`
- 🔲 `apps/acs-e2e/project.json` — Playwright e2e, tags: `scope:acs,type:e2e`
- 🔲 `libs/acs/shell-entry/project.json` — domain, tags: `scope:acs,type:domain`
- 🔲 `libs/acs/data-access/project.json` — data-access, tags: `scope:acs,type:data-access`
- 🔲 `libs/acs/feature-tenant-setup/project.json` — feature, tags: `scope:acs,type:feature`
- 🔲 `libs/acs/feature-caregiver-roster/project.json` — feature, tags: `scope:acs,type:feature`
- 🔲 `libs/acs/feature-caregiver-editor/project.json` — feature, tags: `scope:acs,type:feature`
- 🔲 `libs/acs/feature-patient-linkage/project.json` — feature, tags: `scope:acs,type:feature`
- 🔲 `libs/acs/access/project.json` — domain, tags: `scope:acs,type:domain`
- 🔲 `libs/acs/integration/project.json` — domain, tags: `scope:acs,type:domain`
- 🔲 `libs/acs/navigation/project.json` — domain, tags: `scope:acs,type:domain`
- 🔲 `libs/acs/ui/project.json` — ui, tags: `scope:acs,type:ui`

**Boundary rules:**
- 🔲 `.eslintrc` depConstraints: `scope:acs` cannot depend on `scope:pac|edge|gest`
- 🔲 `nx lint` PASS on all acs libs

**Milestone:**
Wave 1 / P1 Foundation

**Labels:**
type:feature
layer:frontend
scope:acs
priority:P1

**Acceptance criteria:**
- `npx nx lint acs-shell-entry` PASS (0 boundary violations)
- `npx nx build acs` PASS (thin host compiles with empty routes)
- All 10 libs presente em `nx show projects --affected` após scaffolding
- `@neuroassist/acs` resolves to `libs/acs/shell-entry/src/index.ts`

**Dependencies:**
- FE-ACS-001 (contrato define o MODULE_KEY usado nos imports da barrel)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Output de `nx graph` mostrando os 10 libs acs conectados
- Zero violações de boundary no lint

**Risks / Notes:**
- Se o workspace já tiver configurações de path alias conflitantes, o alias `@neuroassist/acs` pode precisar
  de ajuste no `tsconfig.base.json`.
- `apps/acs-e2e` precisa do `baseURL` apontando para o `apps/acs` dev server.
