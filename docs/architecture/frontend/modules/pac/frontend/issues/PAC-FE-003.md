## [PAC-FE-003] PacModule barrel — @neuroassist/pac public API (index.ts)

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
O barrel `@neuroassist/pac` re-exporta `PacModule`, `PAC_ROUTES`, `pac.contract`, `pac.permission-map`, `pac.feature-flags`, `pac.bootstrap`, `pac.nav.config` e `PAC_MODULE_DESCRIPTOR` — tornando o módulo PAC consumível pelo shell `scope:web` via um único import público, sem expor internals de feature libs.

**Context / Problem statement:**
- O shell `scope:web` carrega `@neuroassist/pac` via `loadChildren`; o barrel é o contrato público do módulo (SPEC-06 §1 — "The barrel `@neuroassist/pac` re-exports...").
- Apenas os símbolos explicitamente listados no SPEC-06 §1 devem ser parte da API pública; componentes de feature libs não são re-exportados.
- `PacModule` é um `NgModule` stub ou uma providers-array factory para o Angular Router. No modelo module-as-library (ADR-006), PAC usa standalone components — o "module" é apenas o ponto de entrada do shell.
- Bloqueia: PAC-FE-004, PAC-FE-005, INT-FE-PAC-002

**Approach (execution path):**
1. Criar `libs/pac/shell-entry/src/index.ts` re-exportando: `export * from './lib/pac.contract'`, `export * from './lib/pac.routes'`, `export * from './lib/pac.descriptor'`, `export * from './lib/pac.bootstrap'`, `export * from './lib/pac.module'`.
2. Criar `libs/pac/shell-entry/src/lib/pac.module.ts` exportando `PacModule` (pode ser um bare `@NgModule({ imports: [...RouterModule.forChild(PAC_ROUTES)] })` ou um `provideRouter`-based entrypoint conforme a arquitetura standalone do projeto).
3. Garantir que `project.json` do lib lista `index.ts` como `entryFile`.
4. Verificar que o path-mapping `@neuroassist/pac` em `tsconfig.base.json` aponta para `libs/pac/shell-entry/src/index.ts`.
5. Unit test: importar `@neuroassist/pac` e assert que todos os símbolos obrigatórios estão definidos (smoke test de reexport).

**Non-goals:**
- Não re-exportar `CommandCatalogComponent`, `CommandEditorComponent` nem componentes de UI — esses pertencem a libs internas.
- Não registrar providers globais aqui (providers vão em `pac.bootstrap.ts` — PAC-FE-005).

**Layer(s):**
frontend / libs/pac/shell-entry

**Bounded context:**
pac

**Service execution spec(s):**
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-06-shell-integration.md` (§1)

**Architecture alignment:**
- Module-as-library (ADR-006): sem `forRoot`/`forChild` estático no shell
- Tag Nx: `scope:pac`, `type:domain`

**Implementation checklist (by file):**

**Lib: libs/pac/shell-entry**
- [ ] `libs/pac/shell-entry/src/index.ts` (re-exports)
- [ ] `libs/pac/shell-entry/src/lib/pac.module.ts` (entrypoint para o router)
- [ ] `libs/pac/shell-entry/src/lib/pac.module.spec.ts` (smoke: assert exports)
- [ ] `tsconfig.base.json` — path alias `@neuroassist/pac` → `libs/pac/shell-entry/src/index.ts`

**Tests**
- [ ] `npx nx test pac-shell-entry` PASS
- [ ] `npx nx lint pac-shell-entry` PASS
- [ ] `npx nx build pac-shell-entry` PASS

**Milestone:**
Wave 1 / P1 Foundation

**Labels:**
type:feature
layer:frontend
scope:pac
priority:P1

**Acceptance criteria:**
- `import { PacModule, PAC_ROUTES, MODULE_KEY } from '@neuroassist/pac'` compila sem erros.
- `npx nx build pac-shell-entry` PASS.
- `npx nx lint pac-shell-entry` PASS (sem leaking de `feature-*` internals).

**Dependencies:**
- PAC-FE-001 (pac.contract.ts)
- PAC-FE-002 (pac.routes.ts)
- PAC-FE-004 (pac.descriptor.ts)
- PAC-FE-005 (pac.bootstrap.ts)
- PAC-FE-006 (pac.nav.config)

**Data impact:**
Nenhum.

**API / Contract impact:**
Nenhum direto — é a boundary de exposição pública do módulo.

**Testing / Evidence required:**
- Build clean + tsconfig path resolve no CI.

**Risks / Notes:**
- O alias `@neuroassist/pac` deve ser adicionado ao `tsconfig.base.json` na raiz do Nx workspace; se omitido, o shell não resolve o módulo e o lazy load falha em runtime sem erro em build.
