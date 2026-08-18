## [FE-DASH-002] DashModule + dash.routes.ts — módulo lazy e tabela de rotas

**Status:** 🔲 ABERTA
**GitHub:** [#TBD]

**Outcome:**
`libs/dash/shell-entry/src/lib/dash.module.ts` declara o `DashModule` Angular lazy; `libs/dash/shell-entry/src/lib/dash.routes.ts` exporta `DASH_ROUTES` com as 3 entradas: `/entrada` (chromeless, sem guard), `/` (Home, authGuard) e `/notificacoes` (authGuard).

**Context / Problem statement:**
- O shell monta DASH via `loadChildren: () => import('@neuroassist/dash').then(m => m.DashModule)`.
- Rotas lazy evitam carregar os feature-libs no bundle principal.
- Fonte: SPEC-01 §1/§2; generator-config.yml (features).
- Bloqueia: FE-DASH-011..015, FE-DASH-017, FE-DASH-018

**Approach (execution path):**
1. Criar `dash.module.ts` com `@NgModule({ imports: [RouterModule.forChild(DASH_ROUTES)] })` exportando `DashModule`.
2. Criar `dash.routes.ts` com `DASH_ROUTES: Routes`:
   - `{ path: 'entrada', loadComponent: () => import('.../EntradaPageComponent'), title: 'Entrar no painel' }` (sem guard, sem breadcrumb).
   - `{ path: '', component: HomeStatusPageComponent, canActivate: [authGuard], title: 'Status', data: { breadcrumb: 'Status' } }` (lazy loadComponent).
   - `{ path: 'notificacoes', loadComponent: () => import('.../NotificationCenterPageComponent'), canActivate: [authGuard], title: 'Notificações', data: { breadcrumb: 'Notificações' } }`.
3. `/entrada` não tem `authGuard` e não tem entry no shell chrome (chromeless).
4. Exportar `DashModule` pelo barrel de `dash/shell-entry`.

**Non-goals:**
- Não implementar os componentes das páginas (FE-DASH-011..015).
- Não implementar os guards (FE-DASH-017..018).

**Layer(s):**
frontend / libs/dash/shell-entry (type:domain)

**Bounded context:**
dash

**Service execution spec(s):**
- `docs/architecture/frontend/modules/dash/frontend/frontend-spec/SPEC-01-route-map.md` (§1/§2)

**Architecture alignment:**
- Standalone + OnPush + Signals
- Tag Nx: `scope:dash`, `type:domain`

**Implementation checklist (by file):**

**Lib: libs/dash/shell-entry**
- 🔲 `libs/dash/shell-entry/src/lib/dash.module.ts`
- 🔲 `libs/dash/shell-entry/src/lib/dash.routes.ts`
- 🔲 `libs/dash/shell-entry/src/index.ts` (barrel — export DashModule)

**Tests**
- 🔲 `npx nx test dash-shell-entry` PASS (verificar rota `/entrada` sem guard, `/` e `/notificacoes` com authGuard)
- 🔲 `npx nx lint dash-shell-entry` PASS

**Milestone:**
Wave 1 / Foundation

**Labels:**
type:feature
layer:frontend
scope:dash
priority:P1

**Acceptance criteria:**
- `npx nx test dash-shell-entry` PASS
- `npx nx lint dash-shell-entry` PASS
- `npx nx build dash-shell-entry` PASS
- `DASH_ROUTES` tem exatamente 3 entradas: `entrada`, `''`, `notificacoes`
- `/entrada` não tem `canActivate`
- `/` e `/notificacoes` têm `canActivate: [authGuard]`

**Dependencies:**
- FE-DASH-001 (dash.contract.ts)

**Data impact:**
- Nenhum

**API / Contract impact:**
- Nenhum

**Testing / Evidence required:**
- Test unitário verifica a estrutura do array de rotas

**Risks / Notes:**
- O path `/entrada` é chromeless — verificar se o shell renderiza sem sidebar/topbar quando essa rota está ativa (wiring do shell, não DASH; anotar como premissa para FE-DASH-004).
