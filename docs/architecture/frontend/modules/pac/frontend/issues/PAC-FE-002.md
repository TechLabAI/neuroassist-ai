## [PAC-FE-002] PAC_ROUTES[] — lazy loadChildren + loadComponent sub-routes

**Status:** ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/pac/shell-entry/src/lib/pac.routes.ts` exports `PAC_ROUTES` declarando os três sub-views (`CommandCatalogComponent` em `/pac`, `CommandEditorComponent` em `/pac/comandos/novo` e `/pac/comandos/:comandoId/editar`) com guards corretos e títulos de breadcrumb, todos carregados via `loadComponent` (lazy por componente).

**Context / Problem statement:**
- O shell `scope:web` carrega PAC via `loadChildren: () => import('@neuroassist/pac')` (whole-module lazy); `PAC_ROUTES` declara sub-rotas com `loadComponent` (cada feature lazy). SPEC-01 §1/§2.
- A rota do editor é compartilhada para criar e editar; presença de `:comandoId` seleciona o modo.
- Todas as rotas carregam `canActivate: [authGuard, pacTenantPatientGuard]`; rotas do editor adicionam `pacCommandWriteGuard`.
- Deep-link para `comandoId` desconhecido deve resolver para redirect not-found (SPEC-01 §2 — BE `404`).
- Bloqueia: PAC-FE-003, PAC-FE-014, PAC-FE-015

**Approach (execution path):**
1. Criar `libs/pac/shell-entry/src/lib/pac.routes.ts` com `PAC_ROUTES: Routes = [...]`.
2. Entry `/pac` → `loadComponent(() => import('@neuroassist/pac/feature-command-catalog'))`, `canActivate: [authGuard, pacTenantPatientGuard]`, `title: 'Calibração'`.
3. Entry `/pac/comandos/novo` → `loadComponent(...)` para `CommandEditorComponent`, adicionar `pacCommandWriteGuard`.
4. Entry `/pac/comandos/:comandoId/editar` → mesmo componente, mesmos guards; adicionar `resolve: { comando: pacComandoResolver }` (resolve `Comando` ou redireciona `404 → /not-found`).
5. Criar `pac-comando.resolver.ts` que chama `PacComandoClient.get(pid, cid)`; em `NotFoundException` → redirect `/not-found`.
6. Unit test via `RouterTestingHarness`: verificar paths, guard arrays, `title` fields e redirect 404.

**Non-goals:**
- `PatientRegisterComponent` não é shell-routed (composto dentro do wizard ACS — sem entrada de rota aqui, SPEC-01 §1).
- Rota para GEST live-test — montada no nível do shell page (PAC_ROUTES não referencia GEST).

**Layer(s):**
frontend / libs/pac/shell-entry

**Bounded context:**
pac

**Service execution spec(s):**
- `docs/architecture/frontend/modules/pac/frontend/frontend-spec/SPEC-01-route-map.md` (§1/§2/§3)

**Architecture alignment:**
- Array `Routes` (Angular Router), não `@NgModule.imports`
- Tag Nx: `scope:pac`, `type:domain`
- Sem import de `scope:gest` em nenhuma linha deste arquivo

**Implementation checklist (by file):**

**Lib: libs/pac/shell-entry**
- [ ] `libs/pac/shell-entry/src/lib/pac.routes.ts`
- [ ] `libs/pac/shell-entry/src/lib/pac-comando.resolver.ts` (redirect `/not-found` em 404)
- [ ] `libs/pac/shell-entry/src/lib/pac.routes.spec.ts`

**Tests**
- [ ] `npx nx test pac-shell-entry` PASS
- [ ] `npx nx lint pac-shell-entry` PASS

**Milestone:**
Wave 1 / P1 Foundation

**Labels:**
type:feature
layer:frontend
scope:pac
priority:P1

**Acceptance criteria:**
- `npx nx test pac-shell-entry` PASS: `RouterTestingHarness` navega `/pac` → `CommandCatalogComponent` renderizado.
- `npx nx test pac-shell-entry` PASS: `/pac/comandos/novo` → editor com draft em branco.
- `npx nx test pac-shell-entry` PASS: `comandoId` desconhecido → redirect `/not-found`.
- `npx nx lint pac-shell-entry` PASS (sem imports `scope:gest`).
- `npx nx build pac-shell-entry` PASS.

**Dependencies:**
- PAC-FE-001 (MODULE_KEY, permission map — guards referenciam)
- PAC-FE-017 (pacTenantPatientGuard)
- PAC-FE-018 (pacCommandWriteGuard)

**Data impact:**
Nenhum.

**API / Contract impact:**
- Resolver chama `PacComandoClient.get(pid, cid)` na rota `:comandoId`; 404 → redirect.

**Testing / Evidence required:**
- RouterTestingHarness: 3 rotas navegadas, guard mocks, redirect 404 verificado.

**Risks / Notes:**
- Resolver depende de `pacienteId` ativo no `empresa-context`; garantir que `PacPacienteFacade` foi iniciado pelo bootstrap antes do guard resolver (ver PAC-FE-005).
